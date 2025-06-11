package net.dawson.adorablehamsterpets.client.option;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW; // Keep GLFW import

public class ModKeyBindings {

    public static final String KEY_CATEGORY_HAMSTERPETS = "key.category.adorablehamsterpets.main";
    public static final String KEY_THROW_HAMSTER = "key.adorablehamsterpets.throw_hamster";

    public static KeyBinding THROW_HAMSTER_KEY;

    public static final String KEY_TOGGLE_HAMSTER_DEBUG_TRANSLATION_KEY = "key.adorablehamsterpets.toggle_hamster_debug";
    public static KeyBinding TOGGLE_HAMSTER_DEBUG_KEYBIND;

    public static void registerKeyInputs() {
        THROW_HAMSTER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_THROW_HAMSTER,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY_HAMSTERPETS
        ));
    }
}