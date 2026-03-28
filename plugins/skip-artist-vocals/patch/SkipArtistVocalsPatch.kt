package app.morphe.patches.music.interaction.skipartist

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.music.misc.extension.sharedExtensionPatch
import app.morphe.patches.music.misc.settings.PreferenceScreen
import app.morphe.patches.music.misc.settings.settingsPatch
import app.morphe.patches.music.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import app.morphe.patches.music.shared.MusicActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/music/patches/SkipArtistVocalsPatch;"

@Suppress("unused")
val skipArtistVocalsPatch = bytecodePatch(
    name = "Skip artist vocals",
    description = "Automatically skips songs by sexmane and songs featuring sexmane vocals.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE_MUSIC)

    execute {
        PreferenceScreen.PLAYER.addPreferences(
            SwitchPreference("morphe_music_skip_artist_vocals"),
        )

        MusicActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->init(Landroid/app/Activity;)V",
        )
    }
}
