package com.example.radioarealocator.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ThemeController] 单元测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ThemeControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Test
    fun `getAppSettings returns defaults with empty prefs`() {
        prefs().edit().clear().commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(ColorMode.SYSTEM, settings.colorMode)
        assertEquals(0, settings.keyColor)
        assertEquals(PaletteStyle.TonalSpot, settings.paletteStyle)
        assertEquals(ColorSpec.SpecVersion.Default, settings.colorSpec)
    }

    @Test
    fun `getAppSettings reads custom keyColor`() {
        prefs().edit().clear().putInt("key_color", 0xFF0000.toInt()).commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(0xFF0000.toInt(), settings.keyColor)
    }

    @Test
    fun `getAppSettings in miuix mode without monet converts monet to non-monet`() {
        prefs().edit().clear()
            .putString("ui_mode", "miuix")
            .putBoolean("miuix_monet", false)
            .putInt("color_mode", ColorMode.MONET_DARK.value)
            .commit()

        val settings = ThemeController.getAppSettings(context)
        // MONET_DARK (5) should be converted to DARK (2) since miuixMonet is false
        assertEquals(ColorMode.DARK, settings.colorMode)
    }

    @Test
    fun `getAppSettings in miuix mode with monet converts non-monet to monet`() {
        prefs().edit().clear()
            .putString("ui_mode", "miuix")
            .putBoolean("miuix_monet", true)
            .putInt("color_mode", ColorMode.DARK.value)
            .commit()

        val settings = ThemeController.getAppSettings(context)
        // DARK (2) should be converted to MONET_DARK (5) since miuixMonet is true
        assertEquals(ColorMode.MONET_DARK, settings.colorMode)
    }

    @Test
    fun `getAppSettings in material mode keeps non-monet as-is`() {
        prefs().edit().clear()
            .putString("ui_mode", "material")
            .putInt("color_mode", ColorMode.DARK.value)
            .commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(ColorMode.DARK, settings.colorMode)
    }

    @Test
    fun `getAppSettings reads custom paletteStyle`() {
        prefs().edit().clear()
            .putString("color_style", PaletteStyle.Vibrant.name)
            .commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(PaletteStyle.Vibrant, settings.paletteStyle)
    }

    @Test
    fun `getAppSettings falls back to TonalSpot for invalid paletteStyle`() {
        prefs().edit().clear()
            .putString("color_style", "INVALID_STYLE")
            .commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(PaletteStyle.TonalSpot, settings.paletteStyle)
    }

    @Test
    fun `getAppSettings reads custom colorSpec`() {
        // Use a real enum value different from Default
        val nonDefaultSpec = ColorSpec.SpecVersion.entries.first { it != ColorSpec.SpecVersion.Default }
        prefs().edit().clear()
            .putString("color_spec", nonDefaultSpec.name)
            .commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(nonDefaultSpec, settings.colorSpec)
    }

    @Test
    fun `getAppSettings falls back to Default for invalid colorSpec`() {
        prefs().edit().clear()
            .putString("color_spec", "INVALID_SPEC")
            .commit()

        val settings = ThemeController.getAppSettings(context)
        assertEquals(ColorSpec.SpecVersion.Default, settings.colorSpec)
    }
}
