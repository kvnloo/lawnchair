import android.content.Context;
import android.content.ContextWrapper;
import app.lawnchair.zer0.voice.z0live.Z0LivePhoneTools;
import java.lang.reflect.Field;
import org.json.JSONArray;
import org.json.JSONObject;

public class Z0LiveAccessibilityCatalogTest {
    public static void main(String[] args) throws Exception {
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field field = allocator.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        // No Android constructor or service runs. Context is unused by schema/disabled admission.
        Context context = (Context) allocator.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), ContextWrapper.class);
        JSONArray schemas = Z0LivePhoneTools.schemas(context);
        String[] tools = {"phone_navigation", "phone_ui_tree", "phone_tap", "phone_type_text", "phone_scroll"};
        for (String tool : tools) {
            int found = 0;
            for (int i = 0; i < schemas.length(); i++) if (tool.equals(schemas.getJSONObject(i).getString("name"))) found++;
            if (found != 1 || !Z0LivePhoneTools.supports(context, tool)) throw new AssertionError("Disabled tool omitted: " + tool);
            JSONObject input = new JSONObject();
            if (tool.equals("phone_navigation")) input.put("action", "back");
            if (tool.equals("phone_tap") || tool.equals("phone_type_text") || tool.equals("phone_scroll")) {
                input.put("package", "org.example.notes").put("window_id", 7).put("snapshot", "fixture")
                    .put("by", "id").put("target", "org.example.notes:id/title");
            }
            if (tool.equals("phone_type_text")) input.put("text", "Hello");
            if (tool.equals("phone_scroll")) input.put("direction", "forward");
            JSONObject result = Z0LivePhoneTools.execute(context, tool, input);
            if (result.getBoolean("ok") || !result.getString("status").equals("permission_required") || !result.getString("instruction").contains("Settings > Accessibility")) {
                throw new AssertionError(tool + " must return structured owner guidance: " + result);
            }
        }
        System.out.println("PASS compiled Android catalog: all 5 accessibility tools declared while disabled, real PhoneTools dispatch returns permission_required with guidance");
    }
}
