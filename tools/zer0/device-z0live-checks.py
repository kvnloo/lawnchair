#!/usr/bin/env python3
"""Real-device z0live checks. Requires explicit owner consent, a paired debug APK and broker DB access.

No provider responses or tool outcomes are mocked. Text injection is shell-only in debug builds.
Only controlled test turns are retained. The microphone is explicitly muted during injection.
No system roles/settings are changed; dial/SMS tools only open composers and never send.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'app.lawnchair.play.debug'
PREFIX = 'app.lawnchair.zer0.voice.z0live.'


class DeviceChecks:
    def __init__(self, serial, db, out, only=None):
        self.serial, self.db, self.out = serial, db, Path(out)
        self.only = set(only.split(',')) if only else None
        self.out.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.sid = None
        self.receipts = []

    def adb(self, *args, binary=False):
        # adb concatenates shell argv: quote ON THE DEVICE, not just in subprocess argv.
        if args and args[0] == 'shell':
            args = ('shell', shlex.join(args[1:]))
        return subprocess.check_output(['adb', '-s', self.serial, *args], text=not binary, timeout=25)

    def broadcast(self, *args):
        return self.adb('shell', 'am', 'broadcast', '-n', PACKAGE + '/' + PREFIX + 'Z0LiveDebugReceiver', *args)

    def connect(self):
        return sqlite3.connect('file:' + self.db + '?mode=ro', uri=True)

    def events(self, after=0):
        with self.connect() as db:
            return [(row[0], json.loads(row[1])) for row in db.execute(
                'SELECT id,body FROM events WHERE session_id=? AND id>? ORDER BY id', (self.sid, after))]

    def check_session(self):
        if self.sid is not None:
            with self.connect() as db:
                row = db.execute('SELECT ended FROM sessions WHERE id=?', (self.sid,)).fetchone()
            if not row or row[0]:
                raise RuntimeError('Controlled phone session ended externally; stopping without touching a replacement')

    def idle(self, seconds=100):
        deadline = time.monotonic() + seconds
        while 'result=1' not in self.broadcast('--ez', 'ready', 'true'):
            self.check_session()
            if time.monotonic() > deadline:
                raise TimeoutError('Realtime did not become idle')
            time.sleep(0.5)

    def focus(self):
        return '\n'.join(line.strip() for line in self.adb('shell', 'dumpsys', 'window').splitlines() if 'mCurrentFocus=' in line)

    def ui(self):
        remote = '/sdcard/Download/z0live-test-ui.xml'
        self.adb('shell', 'uiautomator', 'dump', remote)
        try:
            return ET.fromstring(self.adb('exec-out', 'cat', remote))
        finally:
            self.adb('shell', 'rm', remote)

    def tap(self, text, exact=True):
        for node in self.ui().iter('node'):
            labels = (node.get('text', ''), node.get('content-desc', ''))
            if any(label == text if exact else text.lower() in label.lower() for label in labels):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
                self.adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                return True
        return False

    def screenshot(self, name):
        # Keep final evidence frames small before vision; never capture contacts/messages/browser history.
        from PIL import Image
        import io
        frame = Image.open(io.BytesIO(self.adb('exec-out', 'screencap', '-p', binary=True)))
        frame.thumbnail((720, 1560))
        path = self.out / (name + '.png')
        frame.save(path)
        return str(path)

    def start(self):
        started = time.time()
        self.adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'z0live://voice', PACKAGE)
        assert self.tap('Start'), 'Visible Start control not found; pairing/permission must be completed first'
        until = time.monotonic() + 65
        while time.monotonic() < until:
            self.broadcast('--ez', 'muted', 'true')
            if 'result=1' in self.broadcast('--ez', 'ready', 'true'):
                break
            time.sleep(0.4)
        else:
            raise TimeoutError('Phone voice connection did not become ready')
        with self.connect() as db:
            row = db.execute("SELECT id,created,tools FROM sessions WHERE client='lawnchair' AND ended=0 ORDER BY created DESC LIMIT 1").fetchone()
        assert row and row[1] >= started - 2, 'No newly minted phone session'
        self.sid = row[0]
        self.save('session', {'session_id': self.sid, 'created': row[1], 'tools': json.loads(row[2])})
        return self.sid

    def save(self, name, value):
        (self.out / (name + '.json')).write_text(json.dumps(value, indent=2) + '\n')

    def turn(self, name, prompt, tool=None, focus=None, expect_ok=True, seconds=55):
        if self.only is not None and name not in self.only:
            return None
        self.check_session()
        self.idle()
        after = max((ident for ident, _ in self.events()), default=0)
        started = time.time()
        accepted = self.broadcast('--es', 'text', prompt)
        assert 'result=1' in accepted, 'Debug turn was not accepted'
        deadline = time.monotonic() + seconds
        found, complete, focus_samples = [], False, []
        while time.monotonic() < deadline:
            self.check_session()
            found = self.events(after)
            if any(e['type'] == 'tool_result' for _, e in found):
                current = self.focus()
                if not focus_samples or focus_samples[-1]['focus'] != current:
                    focus_samples.append({'ts': time.time(), 'focus': current})
            injected = any(e['type'] == 'user_transcript' and e['text'] == prompt for _, e in found)
            spoken = any(e['type'] == 'assistant_transcript' for _, e in found)
            stopped = any(e['data'].get('source') in ('output_audio_buffer.stopped', 'output_audio_buffer.cleared') for _, e in found)
            if injected and spoken and stopped:
                self.idle(15)
                complete = True
                break
            time.sleep(0.4)
        results = [e['data'] for _, e in found if e['type'] == 'tool_result' and (not tool or e['data'].get('name') == tool)]
        actual_focus = self.focus()
        receipt = {'name': name, 'prompt': prompt, 'session_id': self.sid, 'elapsed_seconds': time.time() - started,
                   'complete': complete, 'tool': tool, 'results': results, 'focus': actual_focus, 'focus_samples': focus_samples,
                   'assistant': [e['text'] for _, e in found if e['type'] == 'assistant_transcript'],
                   'events': [e for _, e in found]}
        receipt['pass'] = complete and (not tool or bool(results) and bool(results[-1].get('success')) == expect_ok) and (not focus or any(focus in sample['focus'] for sample in focus_samples))
        self.save(name, receipt)
        self.receipts.append(receipt)
        print(json.dumps({k: v for k, v in receipt.items() if k != 'events'}), flush=True)
        return receipt

    def torch_state(self):
        lines = self.adb('shell', 'dumpsys', 'media.camera').splitlines()
        return '\n'.join(line for line in lines if 'Torch' in line and ('Status' in line or 'status' in line or 'Client' in line))

    def suite(self):
        self.start()
        self.turn('identity', 'Are you using ChatGPT voice? Briefly explain what z0live is, the model and voice, and how Hermes fits in.')
        self.turn('home', 'Go home', 'go_home', 'app.lawnchair.play.debug')
        self.turn('chrome', 'Open Chrome', 'open_app', 'com.android.chrome')
        self.turn('wifi', 'Open settings wifi', 'open_settings', 'com.android.settings')
        self.turn('torch-on', 'Turn the flashlight on', 'flashlight')
        if self.only is None or 'torch-on' in self.only:
            self.save('torch-on-system', {'state': self.torch_state()})
        self.turn('torch-off', 'Turn the flashlight off', 'flashlight')
        if self.only is None or 'torch-off' in self.only:
            self.save('torch-off-system', {'state': self.torch_state()})
        self.turn('list-apps', 'List apps matching chro. Do not open them.', 'list_apps')
        self.turn('search-apps', 'Search apps for Chrome. Do not open it.', 'search_apps')
        self.turn('open-url', 'Open https://example.com in my browser', 'open_url', 'com.android.chrome')
        self.turn('web-search', 'Search the web for Lawnchair Android launcher', 'web_search', 'com.android.chrome')
        self.turn('volume-up', 'Turn media volume up one step', 'volume')
        self.turn('volume-down', 'Turn media volume down one step', 'volume')
        self.turn('timer', 'Set a timer for 1 minute', 'set_timer')
        # Only inspect the newly created timer screen, never the owner's alarm list.
        if self.only is None or 'timer' in self.only:
            cancelled = self.tap('Cancel') or self.tap('Delete') or self.tap('Reset')
            if not cancelled and self.tap('Pause'):
                cancelled = self.tap('Cancel') or self.tap('Reset')
            self.save('timer-cleanup', {'cancelled_or_reset': cancelled, 'focus': self.focus()})
            if not cancelled:
                raise RuntimeError('Timer cleanup needs owner attention; stop other tests rather than leave a timer ringing')
        self.turn('navigate', 'Open a maps search for Central Park New York. Do not start route guidance.', 'navigate_to')
        self.turn('dial', 'Open the dialer with +15550100123. Do not place a call.', 'start_call')
        self.turn('sms', 'Open an SMS composer to +15550100123 with text z0live automated test. Do not send.', 'send_text')
        self.turn('note', 'Share a note with text z0live automated note test. Do not choose a destination or save it.', 'take_note')
        self.turn('media-pause', 'Pause media', 'media_control')
        self.turn('ripple-unconfigured', 'Open Ripple', 'ripple', expect_ok=False)
        if self.only is None or 'ripple-close' in self.only:
            self.adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'z0live://ripple', PACKAGE)
        self.turn('ripple-close', 'Close Ripple', 'ripple')
        self.turn('what-changed', 'What changed today? Check the actual work history and keep the answer short.', 'what_changed', seconds=70)
        self.turn('hermes', 'Tell Hermes: this is a z0live phone integration test. Reply only Z0LIVE_S25_TEST_OK and do not take any actions.', 'tell_hermes')
        self.broadcast('--ez', 'stats', 'true')
        self.save('suite-summary', {'session_id': self.sid, 'total': len(self.receipts), 'passed': sum(r['pass'] for r in self.receipts),
                                   'failed': [r['name'] for r in self.receipts if not r['pass']],
                                   'scope': 'Real GPT text turns on phone; microphone muted; intent dispatch does not prove downstream media playback or saved note/alarm.'})

    def finish(self):
        with self.connect() as db:
            active = db.execute("SELECT id FROM sessions WHERE client='lawnchair' AND ended=0 ORDER BY created DESC LIMIT 1").fetchone()
        # Never end a different session the owner started while our suite was running.
        if active is None or self.sid is not None and active[0] == self.sid:
            self.broadcast('--ez', 'end', 'true')
            self.adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        else:
            print('STOP: active session changed; leaving owner session untouched', flush=True)
        log = self.adb('logcat', '-d', '-s', 'z0live:*', '*:S')
        (self.out / 'z0live.log').write_text(log)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--broker-db', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', help='Comma-separated case names for a bounded continuation batch')
    parser.add_argument('--consent-real-device-actions', action='store_true', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    checks = DeviceChecks(args.serial, args.broker_db, args.out, args.only)
    try:
        checks.suite()
    finally:
        checks.finish()


if __name__ == '__main__':
    main()
