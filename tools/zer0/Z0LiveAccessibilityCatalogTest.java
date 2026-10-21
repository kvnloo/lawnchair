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
        String[] tools = {"phone_navigation", "phone_ui_tree", "phone_do", "phone_act"};
        for (String tool : tools) {
            int found = 0;
            for (int i = 0; i < schemas.length(); i++) if (tool.equals(schemas.getJSONObject(i).getString("name"))) found++;
            if (found != 1 || !Z0LivePhoneTools.supports(context, tool)) throw new AssertionError("Disabled tool omitted: " + tool);
            JSONObject input = new JSONObject();
            if (tool.equals("phone_navigation")) input.put("action", "back");
            if (tool.equals("phone_do")) input.put("goal", "tap the menu button");
            if (tool.equals("phone_act")) input.put("id", "a1b2c3").put("action", "click");
            JSONObject result = Z0LivePhoneTools.execute(context, tool, input);
            if (result.getBoolean("ok") || !result.getString("status").equals("permission_required") || !result.getString("missing").equals("service_disabled")
                    || !result.getString("instruction").contains("Settings > Accessibility") || !result.getString("instruction").contains("Allow restricted settings")) {
                throw new AssertionError(tool + " must return structured owner guidance: " + result);
            }
        }
        JSONObject tree = null;
        for (int i = 0; i < schemas.length(); i++) if (schemas.getJSONObject(i).getString("name").equals("phone_act")) tree = schemas.getJSONObject(i);
        JSONArray required = tree.getJSONObject("parameters").getJSONArray("required");
        if (required.length() != 1 || !required.getString(0).equals("id")) throw new AssertionError("phone_act requires only id: " + required);
        System.out.println("PASS compiled Android catalog: all 4 accessibility tools declared while disabled, real PhoneTools dispatch returns permission_required with guidance");
    }
}
