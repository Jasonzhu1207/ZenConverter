package org.zenconverter.app.ui

import android.content.Context
import android.media.ExifInterface
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Size
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LanguageOption
import org.zenconverter.app.i18n.LocalizedText
import org.zenconverter.app.metadata.MetadataBackupInfo
import org.zenconverter.app.metadata.MetadataInspection
import org.zenconverter.app.metadata.MetadataMessageKey
import org.zenconverter.app.metadata.MetadataStatusMessage
import org.zenconverter.app.metadata.MetadataTargetKind
import org.zenconverter.app.conversion.TargetId
import org.zenconverter.app.audio.CompressorPreset
import org.zenconverter.app.audio.EqualizerPreset
import org.zenconverter.app.audio.LoudnessTarget
import org.zenconverter.app.audio.MasteringBand
import org.zenconverter.app.audio.MasteringModel
import org.zenconverter.app.audio.MasteringTarget
import org.zenconverter.app.audio.ReverbPreset
import org.zenconverter.app.audio.SilenceRemovalMode
import org.zenconverter.app.audio.StemKind
import org.zenconverter.app.audio.VoiceChangerPreset
import org.zenconverter.app.studio.QualityEstimator
import org.zenconverter.app.studio.SmartPreset
import org.zenconverter.app.settings.ActionBarPosition
import org.zenconverter.app.updates.DownloadProgress
import org.zenconverter.app.updates.UpdateChannel
import org.zenconverter.app.updates.UpdateFailureReason
import org.zenconverter.app.updates.UpdateRelease

internal data class PrivacyPolicySection(
    val title: String,
    val paragraphs: List<String>
)

internal data class PrivacyPolicyText(
    val title: String,
    val back: String,
    val updated: String,
    val intro: String,
    val sections: List<PrivacyPolicySection>,
    val projectPage: String
)

internal data class HelpGuideCopy(
    val title: String,
    val body: String,
    val back: String,
    val videoTitle: String,
    val videoBody: String,
    val videoFormats: String,
    val audioTitle: String,
    val audioBody: String,
    val audioFormats: String,
    val imageTitle: String,
    val imageBody: String,
    val imageFormats: String,
    val documentTitle: String,
    val documentBody: String,
    val documentFormats: String,
    val fontTitle: String,
    val fontBody: String,
    val fontFormats: String,
    val subtitleTitle: String,
    val subtitleBody: String,
    val subtitleFormats: String,
    val privacyTitle: String,
    val privacyBody: String,
    val privacyFormats: String,
    val flowInput: String,
    val flowProcess: String,
    val flowOutput: String,
    val help: String
)

internal class UiText(private val context: Context) {
    val locale: Locale get() = context.resources.configuration.locales[0]

    fun text(@StringRes id: Int, vararg arguments: Any): String =
        if (arguments.isEmpty()) context.getString(id) else context.getString(id, *arguments)

    fun quantity(@PluralsRes id: Int, count: Int, vararg arguments: Any): String =
        if (arguments.isEmpty()) context.resources.getQuantityString(id, count)
        else context.resources.getQuantityString(id, count, *arguments)

    val tagline: String
        get() = text(R.string.ui_tagline)

    val moreHeaderActions: String
        get() = text(R.string.ui_more_header_actions)

    val openMetadataSecurity: String
        get() = text(R.string.ui_open_metadata_security)

    val closeMetadataSecurity: String
        get() = text(R.string.ui_close_metadata_security)

    val openAbout: String
        get() = text(R.string.ui_open_about)

    val closeAbout: String
        get() = text(R.string.ui_close_about)

    val openSettings: String
        get() = text(R.string.ui_open_settings)

    val closeSettings: String
        get() = text(R.string.ui_close_settings)

    val appLogo: String
        get() = text(R.string.ui_app_logo)

    val appVersion: String
        get() = text(R.string.ui_app_version)

    val appLicense: String
        get() = text(R.string.ui_app_license)

    val aboutDescription: String
        get() = text(R.string.ui_about_description)

    val githubRepository: String
        get() = text(R.string.ui_github_repository)

    val privacyPolicy: PrivacyPolicyText
        get() = PrivacyPolicyText(
            title = text(R.string.privacy_title_privacy_policy),
            back = text(R.string.privacy_back_back),
            updated = text(R.string.privacy_updated_last_updated),
            intro = text(R.string.privacy_intro_zenconverter_processes_files_on_your_device_files),
            sections = listOf(
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_file_processing),
                    paragraphs = listOf(
                        text(R.string.privacy_the_app_reads_only_files_you_provide_through_android_s_file_picker_share_or_open_with_if_a_file_provider_cannot_be_used_directly_the_app_creates_a_tempo),
                        text(R.string.privacy_the_app_attempts_to_remove_temporary_files_when_a_task_ends_a_copy_may_remain_in_cache_after_an_unexpected_app_or_device_interruption_the_app_attempts_t)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_metadata_safety),
                    paragraphs = listOf(
                        text(R.string.privacy_metadata_inspection_applies_only_to_files_you_select_image_inspection_may_show_location_capture_time_camera_software_description_and_similar_information),
                        text(R.string.privacy_cleaning_a_jpeg_modifies_the_selected_file_in_place_removed_metadata_is_kept_in_app_private_storage_together_with_the_original_file_name_dimensions_a_ma)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_pdf_passwords),
                    paragraphs = listOf(
                        text(R.string.privacy_a_password_is_used_only_to_open_encrypt_or_decrypt_a_pdf_you_selected_it_is_not_written_to_app_settings_or_task_history_and_it_is_not_sent_over_the_netw)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_network_access_and_external_services),
                    paragraphs = listOf(
                        text(R.string.privacy_the_app_does_not_check_for_updates_in_the_background_builds_that_include_check_for_updates_connect_to_github_only_after_you_tap_it_and_connect_to_github),
                        text(R.string.privacy_like_any_website_github_receives_connection_data_such_as_your_ip_address_and_user_agent_under_its_own_privacy_policy_github_does_not_receive_your_select)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_permissions_and_other_apps),
                    paragraphs = listOf(
                        text(R.string.privacy_file_access_is_used_to_read_selected_inputs_save_results_or_clean_a_jpeg_in_place_older_android_versions_may_also_request_storage_permission_notificatio),
                        text(R.string.privacy_the_github_distributed_build_uses_permission_to_request_package_installation_only_when_you_choose_to_install_a_downloaded_update_the_app_does_not_reques)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_retention_deletion_and_android_backup),
                    paragraphs = listOf(
                        text(R.string.privacy_language_accent_color_whether_a_custom_output_location_is_used_and_the_selected_folder_s_uri_and_display_name_are_stored_in_private_app_settings_you_man),
                        text(R.string.privacy_clearing_app_data_or_uninstalling_removes_on_device_settings_cache_and_private_metadata_backups_android_system_backup_is_currently_enabled_depending_on),
                        text(R.string.privacy_the_app_has_no_online_account_and_stores_no_converted_files_on_a_server_controlled_by_the_developer_the_app_has_no_online_account_and_stores_no_conver)
                    )
                ),
                PrivacyPolicySection(
                    title = text(R.string.privacy_title_contact),
                    paragraphs = listOf(
                        text(R.string.privacy_for_privacy_questions_use_the_current_contact_method_listed_on_the_project_or_distribution_page_do_not_attach_files_passwords_or_sensitive_metadata_to_a)
                    )
                )
            ),
            projectPage = text(R.string.privacy_project_page_open_project_page)
        )

    val helpGuide: HelpGuideCopy
        get() = HelpGuideCopy(
            title = text(R.string.help_title),
            body = text(R.string.help_body),
            back = text(R.string.help_back),
            videoTitle = text(R.string.help_video_title),
            videoBody = text(R.string.help_video_body),
            videoFormats = text(R.string.help_video_formats),
            audioTitle = text(R.string.help_audio_title),
            audioBody = text(R.string.help_audio_body),
            audioFormats = text(R.string.help_audio_formats),
            imageTitle = text(R.string.help_image_title),
            imageBody = text(R.string.help_image_body),
            imageFormats = text(R.string.help_image_formats),
            documentTitle = text(R.string.help_document_title),
            documentBody = text(R.string.help_document_body),
            documentFormats = text(R.string.help_document_formats),
            fontTitle = text(R.string.help_font_title),
            fontBody = text(R.string.help_font_body),
            fontFormats = text(R.string.help_font_formats),
            subtitleTitle = text(R.string.help_subtitle_title),
            subtitleBody = text(R.string.help_subtitle_body),
            subtitleFormats = text(R.string.help_subtitle_formats),
            privacyTitle = text(R.string.help_privacy_title),
            privacyBody = text(R.string.help_privacy_body),
            privacyFormats = text(R.string.help_privacy_formats),
            flowInput = text(R.string.help_flow_input),
            flowProcess = text(R.string.help_flow_process),
            flowOutput = text(R.string.help_flow_output),
            help = text(R.string.help_help)
        )

    val checkUpdates: String
        get() = text(R.string.ui_check_updates)

    val stableUpdateChannel: String
        get() = text(R.string.ui_stable_update_channel)

    val previewUpdateChannel: String
        get() = text(R.string.ui_preview_update_channel)

    val checkingUpdates: String
        get() = text(R.string.ui_checking_updates)

    val appDownload: String
        get() = text(R.string.ui_app_download)

    val browserDownload: String
        get() = text(R.string.ui_browser_download)

    val downloadComplete: String
        get() = text(R.string.ui_download_complete)

    val openDownloadedApk: String
        get() = text(R.string.ui_open_downloaded_apk)

    val downloadFailed: String
        get() = text(R.string.ui_download_failed)

    val cancelDownload: String
        get() = text(R.string.ui_cancel_download)

    val downloadCancelled: String
        get() = text(R.string.ui_download_cancelled)

    val installPermissionRequired: String
        get() = text(R.string.ui_install_permission_required)

    val apkOpenFailed: String
        get() = text(R.string.ui_apk_open_failed)

    val supportDevelopment: String
        get() = text(R.string.ui_support_development)

    val sponsorTitle: String
        get() = text(R.string.ui_sponsor_title)

    val sponsorIntro: String
        get() = text(R.string.ui_sponsor_intro)

    val sponsorNoBenefits: String
        get() = text(R.string.ui_sponsor_no_benefits)

    val openLink: String
        get() = text(R.string.ui_open_link)

    val copy: String
        get() = text(R.string.ui_copy)

    val copied: String
        get() = text(R.string.ui_copied)

    val linkUnavailable: String
        get() = text(R.string.ui_link_unavailable)

    val modelDownload: String
        get() = text(R.string.ui_model_download)

    val modelDownloadNote: String
        get() = text(R.string.ui_model_download_note)

    val modelPurpose: String
        get() = text(R.string.ui_model_purpose)

    val modelPurposeAnime: String
        get() = text(R.string.ui_model_purpose_anime)

    val modelSource: String
        get() = text(R.string.ui_model_source)

    val modelDownloadAction: String
        get() = text(R.string.ui_model_download_action)

    val modelDownloaded: String
        get() = text(R.string.ui_model_downloaded)

    val modelRedownload: String
        get() = text(R.string.ui_model_redownload)

    val officeFontTitle: String
        get() = text(R.string.ui_office_font_title)

    val officeFontSystemReady: String
        get() = text(R.string.ui_office_font_system_ready)

    val officeFontSystemNote: String
        get() = text(R.string.ui_office_font_system_note)

    val officeFontEnhancementNote: String
        get() = text(R.string.ui_office_font_enhancement_note)

    val officeFontSource: String
        get() = text(R.string.ui_office_font_source)

    val metadataSecurityTitle: String
        get() = text(R.string.ui_metadata_security_title)

    val metadataSecurityNote: String
        get() = text(R.string.ui_metadata_security_note)

    val metadataBackupNote: String
        get() = text(R.string.ui_metadata_backup_note)

    val pickMetadataImage: String
        get() = text(R.string.ui_pick_metadata_image)

    val pickMetadataVideo: String
        get() = text(R.string.ui_pick_metadata_video)

    val metadataEmpty: String
        get() = text(R.string.ui_metadata_empty)

    val metadataDetails: String
        get() = text(R.string.ui_metadata_details)

    val metadataCleanAndBackup: String
        get() = text(R.string.ui_metadata_clean_and_backup)

    val metadataRestore: String
        get() = text(R.string.ui_metadata_restore)

    val metadataRestoreTitle: String
        get() = text(R.string.ui_metadata_restore_title)

    val metadataGps: String
        get() = text(R.string.ui_metadata_gps)

    val accentColor: String
        get() = text(R.string.ui_accent_color)

    val themeMode: String
        get() = text(R.string.ui_theme_mode)

    val language: String
        get() = text(R.string.ui_language)

    val addFilesTitle: String
        get() = text(R.string.ui_add_files_title)

    val addFilesNote: String
        get() = text(R.string.ui_add_files_note)

    val addFiles: String
        get() = text(R.string.ui_add_files)

    val importSourceTitle: String
        get() = text(R.string.ui_import_source_title)

    val importAlbumTitle: String
        get() = text(R.string.ui_import_album_title)

    val importAlbumNote: String
        get() = text(R.string.ui_import_album_note)

    val importAlbumDialogNote: String
        get() = text(R.string.ui_import_album_dialog_note)

    val importAlbumImagesTitle: String
        get() = text(R.string.ui_import_album_images_title)

    val importAlbumVideosTitle: String
        get() = text(R.string.ui_import_album_videos_title)

    val importFolderTitle: String
        get() = text(R.string.ui_import_folder_title)

    val importFolderNote: String
        get() = text(R.string.ui_import_folder_note)

    val importFilesTitle: String
        get() = text(R.string.ui_import_files_title)

    val importFilesNote: String
        get() = text(R.string.ui_import_files_note)

    val batchSettings: String
        get() = text(R.string.ui_batch_settings)

    val batchSettingsNote: String
        get() = text(R.string.ui_batch_settings_note)

    val batchOptions: String
        get() = text(R.string.ui_batch_options)

    val batchOptionsNote: String
        get() = text(R.string.ui_batch_options_note)

    val batchMixedTarget: String
        get() = text(R.string.ui_batch_mixed_target)

    val adjustOptions: String
        get() = text(R.string.ui_adjust_options)

    val pdfMergeTitle: String
        get() = text(R.string.ui_pdf_merge_title)

    val pdfMergeNote: String
        get() = text(R.string.ui_pdf_merge_note)

    val createImagePdfMerge: String
        get() = text(R.string.ui_create_image_pdf_merge)

    val createPdfMerge: String
        get() = text(R.string.ui_create_pdf_merge)

    val pdfMergeMember: String
        get() = text(R.string.ui_pdf_merge_member)

    val videoMergeTitle: String
        get() = text(R.string.ui_video_merge_title)

    val videoMergeNote: String
        get() = text(R.string.ui_video_merge_note)

    val createVideoMerge: String
        get() = text(R.string.ui_create_video_merge)

    val videoMergeMember: String
        get() = text(R.string.ui_video_merge_member)

    val addToMerge: String
        get() = text(R.string.ui_add_to_merge)

    val removeMergeGroup: String
        get() = text(R.string.ui_remove_merge_group)

    val target: String
        get() = text(R.string.ui_target)

    val output: String
        get() = text(R.string.ui_output)

    val choose: String
        get() = text(R.string.ui_choose)

    val chooseDirectory: String
        get() = text(R.string.ui_choose_directory)

    val chooseFolderBeforeConversion: String
        get() = text(R.string.ui_choose_folder_before_conversion)

    val defaultOutputLocation: String
        get() = text(R.string.ui_default_output_location)

    val defaultOutputNote: String
        get() = text(R.string.ui_default_output_note)

    val customOutputLocation: String
        get() = text(R.string.ui_custom_output_location)

    val storagePermissionRequired: String
        get() = text(R.string.ui_storage_permission_required)

    val folderPermissionSaved: String
        get() = text(R.string.ui_folder_permission_saved)

    val folderSelectedForSession: String
        get() = text(R.string.ui_folder_selected_for_session)

    val start: String
        get() = text(R.string.ui_start)

    val cancel: String
        get() = text(R.string.ui_cancel)

    val cancelOrClearTasks: String
        get() = text(R.string.ui_cancel_or_clear_tasks)

    val queue: String
        get() = text(R.string.ui_queue)

    val selectedSuffix: String
        get() = text(R.string.ui_selected_suffix)

    val unknownType: String
        get() = text(R.string.ui_unknown_type)

    val unknownSize: String
        get() = text(R.string.ui_unknown_size)

    val remove: String
        get() = text(R.string.ui_remove)

    val shareOutput: String
        get() = text(R.string.ui_share_output)

    val openOutputLocation: String
        get() = text(R.string.ui_open_output_location)

    val outputUnavailable: String
        get() = text(R.string.ui_output_unavailable)

    val shareOutputFailed: String
        get() = text(R.string.ui_share_output_failed)

    val openOutputFailed: String
        get() = text(R.string.ui_open_output_failed)

    val waiting: String
        get() = text(R.string.ui_waiting)

    val processing: String
        get() = text(R.string.ui_processing)

    val flowComplete: String
        get() = text(R.string.ui_flow_complete)

    val flowCompleteNoFiles: String
        get() = text(R.string.ui_flow_complete_no_files)

    val cancelled: String
        get() = text(R.string.ui_cancelled)

    val failed: String
        get() = text(R.string.ui_failed)

    val quality: String
        get() = text(R.string.ui_quality)

    val pageSize: String
        get() = text(R.string.ui_page_size)

    val renderQuality: String
        get() = text(R.string.ui_render_quality)

    val compressionPreset: String
        get() = text(R.string.ui_compression_preset)

    val resolution: String
        get() = text(R.string.ui_resolution)

    val superResolution: String
        get() = text(R.string.ui_super_resolution)

    val resize: String
        get() = text(R.string.ui_resize)

    val videoCompressionMode: String
        get() = text(R.string.ui_video_compression_mode)

    val videoFrameInterpolation: String
        get() = text(R.string.ui_video_frame_interpolation)

    val videoInterpolationSummary: String
        get() = text(R.string.ui_video_interpolation_summary)

    val videoInterpolationOpticalFlowSummary: String
        get() = text(R.string.ui_video_interpolation_optical_flow_summary)

    val rifeModelPurpose: String
        get() = text(R.string.ui_rife_model_purpose)

    val bitrate: String
        get() = text(R.string.ui_bitrate)

    val codec: String
        get() = text(R.string.ui_codec)

    val frameRate: String
        get() = text(R.string.ui_frame_rate)

    val sampleRate: String
        get() = text(R.string.ui_sample_rate)

    val bitDepth: String
        get() = text(R.string.ui_bit_depth)

    val gifFrameRate: String
        get() = text(R.string.ui_gif_frame_rate)

    val gifColors: String
        get() = text(R.string.ui_gif_colors)

    val opusSampleRateHint: String
        get() = text(R.string.ui_opus_sample_rate_hint)

    val vorbisHint: String
        get() = text(R.string.ui_vorbis_hint)

    val channels: String
        get() = text(R.string.ui_channels)

    val trimRange: String
        get() = text(R.string.ui_trim_range)

    val trimQuick: String
        get() = text(R.string.ui_trim_quick)

    val trimPrecise: String
        get() = text(R.string.ui_trim_precise)

    val trimStartSeconds: String
        get() = text(R.string.ui_trim_start_seconds)

    val trimEndSeconds: String
        get() = text(R.string.ui_trim_end_seconds)

    val trimSplitPoints: String
        get() = text(R.string.ui_trim_split_points)

    val trimAddSplitPoint: String
        get() = text(R.string.ui_trim_add_split_point)

    val trimSplitPointsOrder: String
        get() = text(R.string.ui_trim_split_points_order)

    val trimSplitPointsWithinDuration: String
        get() = text(R.string.ui_trim_split_points_within_duration)

    val trimDurationUnknown: String
        get() = text(R.string.ui_trim_duration_unknown)

    val trimRangeTooLarge: String
        get() = text(R.string.ui_trim_range_too_large)

    val trimStartBeforeDuration: String
        get() = text(R.string.ui_trim_start_before_duration)

    val trimEndAfterStart: String
        get() = text(R.string.ui_trim_end_after_start)

    val trimEndWithinDuration: String
        get() = text(R.string.ui_trim_end_within_duration)

    val gifFrameMode: String
        get() = text(R.string.ui_gif_frame_mode)

    val password: String
        get() = text(R.string.ui_password)

    val skip: String
        get() = text(R.string.ui_skip)

    val pdfPasswordTitle: String
        get() = text(R.string.ui_pdf_password_title)

    val pdfOutputPasswordTitle: String
        get() = text(R.string.ui_pdf_output_password_title)

    val toPrefix: String
        get() = text(R.string.ui_to_prefix)

    val contactSheetGridLabel: String
        get() = text(R.string.ui_contact_sheet_grid_label)

    val contactSheetIncludeHeader: String
        get() = text(R.string.ui_contact_sheet_include_header)

    val contactSheetIncludeTimestamp: String
        get() = text(R.string.ui_contact_sheet_include_timestamp)

    fun selectedCount(count: Int): String = quantity(R.plurals.count_selected, count, count)

    fun batchCount(count: Int): String {
        return quantity(R.plurals.count_batch_count, count, count)
    }

    fun pdfMergeGroupTitle(type: PdfMergeType): String {
        return when (type) {
            PdfMergeType.Images -> createImagePdfMerge
            PdfMergeType.Pdfs -> createPdfMerge
        }
    }

    fun externalImportTargetLabel(target: ExternalImportTarget): String {
        val formatLabel = optionValue(target.targetFormat.key)
        if (target.targetFormat.id.isContactSheet
        ) {
            return formatLabel
        }
        return "${categoryLabel(target.category)} · $formatLabel"
    }

    fun externalImportTargetPureLabel(target: ExternalImportTarget): String {
        return when (target.targetFormat.id) {
            TargetId.ContactSheetJpg -> "JPG"
            TargetId.ContactSheetPng -> "PNG"
            TargetId.M4a -> "M4A"
            TargetId.PdfCompress -> optionValue("pdf_compress")
            TargetId.PdfEncrypt -> optionValue("pdf_encrypt")
            TargetId.PdfDecrypt -> optionValue("pdf_decrypt")
            else -> optionValue(target.targetFormat.key)
        }
    }

    val categoryContactSheet: String get() = text(R.string.category_contact_sheet)
    val settingsActionBarPosition: String get() = text(R.string.settings_action_bar_position)
    val settingsActionBarPositionBottom: String get() = text(R.string.settings_action_bar_position_bottom)
    val settingsActionBarPositionTop: String get() = text(R.string.settings_action_bar_position_top)
    val settingsActionBarPositionDesc: String get() = text(R.string.settings_action_bar_position_desc)
    val clearTasks: String get() = text(R.string.ui_clear_tasks)
    val startConversion: String get() = text(R.string.ui_start)
    fun startConversion(count: Int): String = text(R.string.ui_start_conversion, count)

    fun actionBarPositionLabel(position: ActionBarPosition): String {
        return when (position) {
            ActionBarPosition.Bottom -> settingsActionBarPositionBottom
            ActionBarPosition.Top -> settingsActionBarPositionTop
        }
    }

    fun fileCountLabel(count: Int): String {
        return quantity(R.plurals.count_file_count_label, count, count)
    }

    fun pageCountLabel(count: Int): String {
        return quantity(R.plurals.count_page_count_label, count, count)
    }

    fun frameRateLabel(value: String): String {
        return text(R.string.text_frame_rate_label, value)
    }

    fun bitrateLabel(value: String): String {
        return text(R.string.text_bitrate_label, value)
    }

    fun outputLargerHint(): String {
        return text(R.string.text_output_larger_hint)
    }

    fun compressionPresetSummary(value: String): String {
        return when (value) {
            VIDEO_COMPRESSION_VISUAL_LOSSLESS -> text(R.string.text_compression_preset_summary)
            VIDEO_COMPRESSION_BALANCED -> text(R.string.text_compression_preset_summary_h_265_preferred_short_side_1080p_original_frame_rate_aac_160_kbps)
            VIDEO_COMPRESSION_SMALL -> text(R.string.text_compression_preset_summary_h_265_preferred_short_side_720p_max_30_fps_aac_128_kbps)
            else -> ""
        }
    }

    fun superResolutionSummary(): String {
        return text(R.string.text_super_resolution_summary)
    }

    fun aiSuperResolutionSummary(): String {
        return text(R.string.text_ai_super_resolution_summary)
    }

    fun aiUpscaleHint(): String {
        return text(R.string.text_ai_upscale_hint)
    }

    fun rifeInterpolationHint(): String {
        return text(R.string.text_rife_interpolation_hint)
    }

    fun audioBitrateLabel(): String {
        return text(R.string.text_audio_bitrate_label)
    }

    fun audioEncodingModeLabel(): String {
        return text(R.string.text_audio_encoding_mode_label)
    }

    fun mp3VbrQualityLabel(): String {
        return text(R.string.text_mp3_vbr_quality_label)
    }

    fun mp3VbrQualityHint(): String {
        return text(R.string.text_mp3_vbr_quality_hint)
    }

    fun trimDurationHint(durationText: String?): String {
        return durationText?.let { text(R.string.text_trim_duration_hint, it) } ?: trimDurationUnknown
    }

    fun trimSplitSegmentsHint(segmentCount: Int): String {
        return quantity(R.plurals.count_trim_split_segments_hint, segmentCount, segmentCount)
    }

    fun videoAdvancedTitle(): String {
        return text(R.string.text_video_advanced_title)
    }

    fun videoAdvancedNote(): String {
        return text(R.string.text_video_advanced_note)
    }

    fun audioAdvancedTitle(): String {
        return text(R.string.text_audio_advanced_title)
    }

    fun audioAdvancedNote(): String {
        return text(R.string.text_audio_advanced_note)
    }

    fun reverseLabel(): String {
        return text(R.string.text_reverse_label)
    }

    fun fadeInLabel(): String {
        return text(R.string.text_fade_in_label)
    }

    fun fadeOutLabel(): String {
        return text(R.string.text_fade_out_label)
    }

    fun mirrorLabel(): String {
        return text(R.string.text_mirror_label)
    }

    fun rotationLabel(): String {
        return text(R.string.text_rotation_label)
    }

    fun aspectRatioLabel(): String {
        return text(R.string.text_aspect_ratio_label)
    }

    fun motionBlurLabel(): String {
        return text(R.string.text_motion_blur_label)
    }

    fun volumeLabel(): String {
        return text(R.string.text_volume_label)
    }

    fun echoLabel(): String {
        return text(R.string.text_echo_label)
    }

    fun noiseReductionLabel(): String {
        return text(R.string.text_noise_reduction_label)
    }

    fun audioEffectsTitle(): String {
        return text(R.string.ui_audio_effects_section)
    }

    fun equalizerLabel(): String {
        return text(R.string.ui_audio_effects_equalizer)
    }

    fun loudnessLabel(): String {
        return text(R.string.ui_audio_effects_loudness)
    }

    fun compressorLabel(): String {
        return text(R.string.ui_audio_effects_compressor)
    }

    fun reverbLabel(): String {
        return text(R.string.ui_audio_effects_reverb)
    }

    fun voiceChangerLabel(): String {
        return text(R.string.ui_audio_effects_voice)
    }

    fun silenceRemovalLabel(): String {
        return text(R.string.ui_audio_effects_silence)
    }

    fun deHumLabel(): String {
        return text(R.string.ui_audio_effects_de_hum)
    }

    fun equalizerPresetLabel(preset: EqualizerPreset): String {
        return text(
            when (preset) {
                EqualizerPreset.Off -> R.string.ui_audio_effects_preset_off
                EqualizerPreset.Flat -> R.string.ui_audio_effects_preset_flat
                EqualizerPreset.BassBoost -> R.string.ui_audio_effects_preset_bass_boost
                EqualizerPreset.Podcast -> R.string.ui_audio_effects_preset_podcast
                EqualizerPreset.Vocal -> R.string.ui_audio_effects_preset_vocal
                EqualizerPreset.Treble -> R.string.ui_audio_effects_preset_treble
                EqualizerPreset.Loudness -> R.string.ui_audio_effects_preset_loudness
            }
        )
    }

    fun loudnessTargetLabel(target: LoudnessTarget): String {
        return text(
            when (target) {
                LoudnessTarget.Off -> R.string.ui_audio_effects_preset_off
                LoudnessTarget.Podcast -> R.string.ui_audio_effects_preset_podcast
                LoudnessTarget.Streaming -> R.string.ui_audio_effects_preset_streaming
                LoudnessTarget.YouTube -> R.string.ui_audio_effects_preset_youtube
                LoudnessTarget.Spotify -> R.string.ui_audio_effects_preset_spotify
                LoudnessTarget.Broadcast -> R.string.ui_audio_effects_preset_broadcast
            }
        )
    }

    fun compressorPresetLabel(preset: CompressorPreset): String {
        return text(
            when (preset) {
                CompressorPreset.Off -> R.string.ui_audio_effects_preset_off
                CompressorPreset.Gentle -> R.string.ui_audio_effects_preset_gentle
                CompressorPreset.Podcast -> R.string.ui_audio_effects_preset_podcast
                CompressorPreset.Aggressive -> R.string.ui_audio_effects_preset_aggressive
                CompressorPreset.Limiter -> R.string.ui_audio_effects_preset_limiter
            }
        )
    }

    fun reverbPresetLabel(preset: ReverbPreset): String {
        return text(
            when (preset) {
                ReverbPreset.Off -> R.string.ui_audio_effects_preset_off
                ReverbPreset.SmallRoom -> R.string.ui_audio_effects_preset_small_room
                ReverbPreset.LargeHall -> R.string.ui_audio_effects_preset_large_hall
                ReverbPreset.Cathedral -> R.string.ui_audio_effects_preset_cathedral
                ReverbPreset.Plate -> R.string.ui_audio_effects_preset_plate
            }
        )
    }

    fun voiceChangerPresetLabel(preset: VoiceChangerPreset): String {
        return text(
            when (preset) {
                VoiceChangerPreset.Off -> R.string.ui_audio_effects_preset_off
                VoiceChangerPreset.Child -> R.string.ui_audio_effects_preset_child
                VoiceChangerPreset.Deep -> R.string.ui_audio_effects_preset_deep
                VoiceChangerPreset.Robot -> R.string.ui_audio_effects_preset_robot
                VoiceChangerPreset.Chipmunk -> R.string.ui_audio_effects_preset_chipmunk
                VoiceChangerPreset.Monster -> R.string.ui_audio_effects_preset_monster
            }
        )
    }

    fun silenceRemovalModeLabel(mode: SilenceRemovalMode): String {
        return text(
            when (mode) {
                SilenceRemovalMode.Off -> R.string.ui_audio_effects_preset_off
                SilenceRemovalMode.Light -> R.string.ui_audio_effects_preset_light
                SilenceRemovalMode.Standard -> R.string.ui_audio_effects_preset_standard
                SilenceRemovalMode.Aggressive -> R.string.ui_audio_effects_preset_aggressive
            }
        )
    }

    fun smartPresetLabel(preset: SmartPreset): String {
        return text(
            when (preset) {
                SmartPreset.WhatsApp -> R.string.ui_smart_preset_whatsapp
                SmartPreset.EmailUnder25Mb -> R.string.ui_smart_preset_email
                SmartPreset.InstagramReel -> R.string.ui_smart_preset_reel
                SmartPreset.Podcast -> R.string.ui_smart_preset_podcast
                SmartPreset.Youtube1080p -> R.string.ui_smart_preset_youtube
                SmartPreset.Ringtone -> R.string.ui_smart_preset_ringtone
            }
        )
    }

    fun smartPresetSection(): String {
        return text(R.string.ui_smart_preset_section)
    }

    fun targetSizeMode(): String {
        return text(R.string.ui_target_size_mode)
    }

    fun targetSizeHint(): String {
        return text(R.string.ui_target_size_hint)
    }

    fun targetSizeInvalid(): String {
        return text(R.string.ui_target_size_invalid)
    }

    fun targetSizeApply(): String {
        return text(R.string.ui_target_size_apply)
    }

    fun qualityEstimatorTitle(): String {
        return text(R.string.ui_quality_estimator)
    }

    fun qualityEstimatorSize(formattedSize: String): String {
        return text(R.string.ui_quality_estimator_size, formattedSize)
    }

    fun qualityEstimatorQuality(band: QualityEstimator.QualityBand): String {
        val label = text(
            when (band) {
                QualityEstimator.QualityBand.High -> R.string.ui_quality_band_high
                QualityEstimator.QualityBand.Balanced -> R.string.ui_quality_band_balanced
                QualityEstimator.QualityBand.Reduced -> R.string.ui_quality_band_reduced
                QualityEstimator.QualityBand.Low -> R.string.ui_quality_band_low
            }
        )
        return text(R.string.ui_quality_estimator_quality, label)
    }


    // Audio & tools studio ---------------------------------------------------
    fun studioTitle(): String = text(R.string.studio_title)
    fun studioBack(): String = text(R.string.studio_back)
    fun studioWaveform(): String = text(R.string.studio_waveform)
    fun studioWaveformDesc(): String = text(R.string.studio_waveform_desc)
    fun studioRecorder(): String = text(R.string.studio_recorder)
    fun studioRecorderDesc(): String = text(R.string.studio_recorder_desc)
    fun studioMixer(): String = text(R.string.studio_mixer)
    fun studioMixerDesc(): String = text(R.string.studio_mixer_desc)
    fun studioTagEditor(): String = text(R.string.studio_tag_editor)
    fun studioTagEditorDesc(): String = text(R.string.studio_tag_editor_desc)
    fun studioRingtone(): String = text(R.string.studio_ringtone)
    fun studioRingtoneDesc(): String = text(R.string.studio_ringtone_desc)
    fun studioTts(): String = text(R.string.studio_tts)
    fun studioTtsDesc(): String = text(R.string.studio_tts_desc)
    fun studioTranscribe(): String = text(R.string.studio_transcribe)
    fun studioTranscribeDesc(): String = text(R.string.studio_transcribe_desc)
    fun studioStems(): String = text(R.string.studio_stems)
    fun studioStemsDesc(): String = text(R.string.studio_stems_desc)
    fun studioVisualizer(): String = text(R.string.studio_visualizer)
    fun studioVisualizerDesc(): String = text(R.string.studio_visualizer_desc)
    fun studioAnalyze(): String = text(R.string.studio_analyze)
    fun studioAnalyzeDesc(): String = text(R.string.studio_analyze_desc)
    fun studioRecipes(): String = text(R.string.studio_recipes)
    fun studioRecipesDesc(): String = text(R.string.studio_recipes_desc)
    fun studioHistory(): String = text(R.string.studio_history)
    fun studioHistoryDesc(): String = text(R.string.studio_history_desc)
    fun studioPlugins(): String = text(R.string.studio_plugins)
    fun studioPluginsDesc(): String = text(R.string.studio_plugins_desc)
    fun studioWifi(): String = text(R.string.studio_wifi)
    fun studioWifiDesc(): String = text(R.string.studio_wifi_desc)
    fun studioCleaner(): String = text(R.string.studio_storage_cleaner)
    fun studioCleanerDesc(): String = text(R.string.studio_storage_cleaner_desc)
    fun studioCommandView(): String = text(R.string.studio_command_view)
    fun studioCommandViewDesc(): String = text(R.string.studio_command_view_desc)
    fun studioAccessibility(): String = text(R.string.studio_accessibility)
    fun studioAccessibilityDesc(): String = text(R.string.studio_accessibility_desc)
    fun studioSectionAudio(): String = text(R.string.studio_section_audio)
    fun studioSectionCreative(): String = text(R.string.studio_section_creative)
    fun studioSectionSmart(): String = text(R.string.studio_section_smart)
    fun studioSectionPrivacy(): String = text(R.string.studio_section_privacy)
    fun waveformTitle(): String = text(R.string.waveform_title)
    fun waveformLoad(): String = text(R.string.waveform_load)
    fun waveformEmpty(): String = text(R.string.waveform_empty)
    fun waveformAnalyzing(): String = text(R.string.waveform_analyzing)
    fun waveformTrimStart(): String = text(R.string.waveform_trim_start)
    fun waveformTrimEnd(): String = text(R.string.waveform_trim_end)
    fun waveformSplit(): String = text(R.string.waveform_split)
    fun waveformFadeIn(): String = text(R.string.waveform_fade_in)
    fun waveformFadeOut(): String = text(R.string.waveform_fade_out)
    fun waveformApply(): String = text(R.string.waveform_apply)
    fun waveformPreview(): String = text(R.string.waveform_preview)
    fun waveformPreviewBusy(): String = text(R.string.waveform_preview_busy)
    fun recorderTitle(): String = text(R.string.recorder_title)
    fun recorderRecord(): String = text(R.string.recorder_record)
    fun recorderPause(): String = text(R.string.recorder_pause)
    fun recorderResume(): String = text(R.string.recorder_resume)
    fun recorderStop(): String = text(R.string.recorder_stop)
    fun recorderSaveWav(): String = text(R.string.recorder_save_wav)
    fun recorderSaveFlac(): String = text(R.string.recorder_save_flac)
    fun recorderSaveM4a(): String = text(R.string.recorder_save_m4a)
    fun recorderPermissionNeeded(): String = text(R.string.recorder_permission_needed)
    fun recorderLevel(): String = text(R.string.recorder_level)
    fun recorderSaved(): String = text(R.string.recorder_saved)
    fun mixerTitle(): String = text(R.string.mixer_title)
    fun mixerAddTrack(): String = text(R.string.mixer_add_track)
    fun mixerVolume(): String = text(R.string.mixer_volume)
    fun mixerPan(): String = text(R.string.mixer_pan)
    fun mixerMute(): String = text(R.string.mixer_mute)
    fun mixerExport(): String = text(R.string.mixer_export)
    fun mixerEmpty(): String = text(R.string.mixer_empty)
    fun tagTitle(): String = text(R.string.tag_title)
    fun tagLoad(): String = text(R.string.tag_load)
    fun tagTitleField(): String = text(R.string.tag_title_field)
    fun tagArtist(): String = text(R.string.tag_artist)
    fun tagAlbum(): String = text(R.string.tag_album)
    fun tagAlbumArtist(): String = text(R.string.tag_album_artist)
    fun tagTrack(): String = text(R.string.tag_track)
    fun tagYear(): String = text(R.string.tag_year)
    fun tagGenre(): String = text(R.string.tag_genre)
    fun tagComment(): String = text(R.string.tag_comment)
    fun tagLyrics(): String = text(R.string.tag_lyrics)
    fun tagArtwork(): String = text(R.string.tag_artwork)
    fun tagPickArtwork(): String = text(R.string.tag_pick_artwork)
    fun tagSave(): String = text(R.string.tag_save)
    fun tagSaved(): String = text(R.string.tag_saved)
    fun tagBatchNote(): String = text(R.string.tag_batch_note)
    fun ringtoneTitle(): String = text(R.string.ringtone_title)
    fun ringtoneLoad(): String = text(R.string.ringtone_load)
    fun ringtoneStart(): String = text(R.string.ringtone_start)
    fun ringtoneEnd(): String = text(R.string.ringtone_end)
    fun ringtoneSetRingtone(): String = text(R.string.ringtone_set_ringtone)
    fun ringtoneSetAlarm(): String = text(R.string.ringtone_set_alarm)
    fun ringtoneSetNotification(): String = text(R.string.ringtone_set_notification)
    fun ringtoneApplied(): String = text(R.string.ringtone_applied)
    fun ttsTitle(): String = text(R.string.tts_title)
    fun ttsTextHint(): String = text(R.string.tts_text_hint)
    fun ttsSpeak(): String = text(R.string.tts_speak)
    fun ttsExport(): String = text(R.string.tts_export)
    fun ttsUnavailable(): String = text(R.string.tts_unavailable)
    fun ttsExporting(): String = text(R.string.tts_exporting)
    fun ttsExported(): String = text(R.string.tts_exported)
    fun transcribeTitle(): String = text(R.string.transcribe_title)
    fun transcribeLoad(): String = text(R.string.transcribe_load)
    fun transcribeStart(): String = text(R.string.transcribe_start)
    fun transcribeModelNeeded(): String = text(R.string.transcribe_model_needed)
    fun transcribeDownloadModel(): String = text(R.string.transcribe_download_model)
    fun transcribeRunning(): String = text(R.string.transcribe_running)
    fun transcribeExportSrt(): String = text(R.string.transcribe_export_srt)
    fun transcribeExportVtt(): String = text(R.string.transcribe_export_vtt)
    fun transcribeExportTxt(): String = text(R.string.transcribe_export_txt)
    fun transcribeCutWords(): String = text(R.string.transcribe_cut_words)
    fun transcribeEmpty(): String = text(R.string.transcribe_empty)
    fun stemsTitle(): String = text(R.string.stems_title)
    fun stemsLoad(): String = text(R.string.stems_load)
    fun stemsModelNeeded(): String = text(R.string.stems_model_needed)
    fun stemsDownloadModel(): String = text(R.string.stems_download_model)
    fun stemsRun(): String = text(R.string.stems_run)
    fun stemsRunning(): String = text(R.string.stems_running)
    fun stemsVocals(): String = text(R.string.stems_vocals)
    fun stemsDrums(): String = text(R.string.stems_drums)
    fun stemsBass(): String = text(R.string.stems_bass)
    fun stemsOther(): String = text(R.string.stems_other)
    fun visualizerTitle(): String = text(R.string.visualizer_title)
    fun visualizerLoad(): String = text(R.string.visualizer_load)
    fun visualizerArtwork(): String = text(R.string.visualizer_artwork)
    fun visualizerRender(): String = text(R.string.visualizer_render)
    fun visualizerRendering(): String = text(R.string.visualizer_rendering)
    fun analyzeTitle(): String = text(R.string.analyze_title)
    fun analyzeLoad(): String = text(R.string.analyze_load)
    fun analyzeRun(): String = text(R.string.analyze_run)
    fun analyzeRunning(): String = text(R.string.analyze_running)
    fun recipesTitle(): String = text(R.string.recipes_title)
    fun recipesBuiltin(): String = text(R.string.recipes_builtin)
    fun recipesCustom(): String = text(R.string.recipes_custom)
    fun recipesApply(): String = text(R.string.recipes_apply)
    fun recipesImport(): String = text(R.string.recipes_import)
    fun recipesExport(): String = text(R.string.recipes_export)
    fun recipesImportFailed(): String = text(R.string.recipes_import_failed)
    fun recipesExported(): String = text(R.string.recipes_exported)
    fun historyTitle(): String = text(R.string.history_title)
    fun historyEmpty(): String = text(R.string.history_empty)
    fun historyRerun(): String = text(R.string.history_rerun)
    fun historyRestore(): String = text(R.string.history_restore)
    fun historyClear(): String = text(R.string.history_clear)
    fun historyRestored(): String = text(R.string.history_restored)
    fun abPlayerTitle(): String = text(R.string.ab_player_title)
    fun abOriginal(): String = text(R.string.ab_original)
    fun abConverted(): String = text(R.string.ab_converted)
    fun abNoOutput(): String = text(R.string.ab_no_output)
    fun privacyScrubTitle(): String = text(R.string.privacy_scrub_title)
    fun privacyScrubDesc(): String = text(R.string.privacy_scrub_desc)
    fun privacyScrubRun(): String = text(R.string.privacy_scrub_run)
    fun privacyScrubDone(): String = text(R.string.privacy_scrub_done)
    fun guardTitle(): String = text(R.string.guard_title)
    fun guardDesc(): String = text(R.string.guard_desc)
    fun guardPausedThermal(): String = text(R.string.guard_paused_thermal)
    fun guardPausedBattery(): String = text(R.string.guard_paused_battery)
    fun guardResumed(): String = text(R.string.guard_resumed)
    fun wifiTitle(): String = text(R.string.wifi_title)
    fun wifiDesc(): String = text(R.string.wifi_desc)
    fun wifiStart(): String = text(R.string.wifi_start)
    fun wifiStop(): String = text(R.string.wifi_stop)
    fun wifiNoFiles(): String = text(R.string.wifi_no_files)
    fun wifiRunning(): String = text(R.string.wifi_running)
    fun pluginsTitle(): String = text(R.string.plugins_title)
    fun pluginsDesc(): String = text(R.string.plugins_desc)
    fun pluginsImport(): String = text(R.string.plugins_import)
    fun pluginsExport(): String = text(R.string.plugins_export)
    fun pluginsImportFailed(): String = text(R.string.plugins_import_failed)
    fun pluginsExported(): String = text(R.string.plugins_exported)
    fun pluginsNone(): String = text(R.string.plugins_none)
    fun cleanerTitle(): String = text(R.string.cleaner_title)
    fun cleanerScan(): String = text(R.string.cleaner_scan)
    fun cleanerScanning(): String = text(R.string.cleaner_scanning)
    fun cleanerDuplicates(): String = text(R.string.cleaner_duplicates)
    fun cleanerCompress(): String = text(R.string.cleaner_compress)
    fun cleanerSaved(): String = text(R.string.cleaner_saved)
    fun cleanerEmpty(): String = text(R.string.cleaner_empty)
    fun commandTitle(): String = text(R.string.command_title)
    fun commandDesc(): String = text(R.string.command_desc)
    fun commandEmpty(): String = text(R.string.command_empty)
    fun commandCopy(): String = text(R.string.command_copy)
    fun commandCopied(): String = text(R.string.command_copied)
    fun a11ySettingsTitle(): String = text(R.string.a11y_settings_title)
    fun a11yLargeText(): String = text(R.string.a11y_large_text)
    fun a11yLargeTextDesc(): String = text(R.string.a11y_large_text_desc)
    fun a11yAudioDescriptions(): String = text(R.string.a11y_audio_descriptions)
    fun a11yAudioDescriptionsDesc(): String = text(R.string.a11y_audio_descriptions_desc)
    fun settingsStudioEntry(): String = text(R.string.settings_studio_entry)
    fun settingsStudioEntryDesc(): String = text(R.string.settings_studio_entry_desc)


    fun waveformSelection(selected: String, total: String): String =
        text(R.string.waveform_selection, selected, total)

    fun recorderElapsed(elapsed: String): String = text(R.string.recorder_elapsed, elapsed)

    fun mixerTrack(index: Int): String = text(R.string.mixer_track, index)

    fun analyzeBpm(value: String): String = text(R.string.analyze_bpm, value)

    fun analyzeKey(value: String): String = text(R.string.analyze_key, value)

    fun recipesImported(count: Int): String = text(R.string.recipes_imported, count)

    fun recipesStep(index: Int): String = text(R.string.recipes_step, index)

    fun historySaved(value: String): String = text(R.string.history_saved, value)

    fun historyGrew(value: String): String = text(R.string.history_grew, value)

    fun guardBatteryThreshold(percent: Int): String =
        text(R.string.guard_battery_threshold, percent)

    fun wifiAddress(address: String): String = text(R.string.wifi_address, address)

    fun cleanerFound(count: Int, size: String): String =
        text(R.string.cleaner_found, count, size)

    fun cleanerDuplicates(count: Int): String = text(R.string.cleaner_duplicates, count)

    fun cleanerSaved(size: String): String = text(R.string.cleaner_saved, size)

    fun pluginsImported(count: Int): String = text(R.string.plugins_imported, count)

    fun targetSizeMbLabel(): String = text(R.string.ui_target_size_mb)

    fun studioPickFolder(): String = text(R.string.studio_pick_folder)
    fun studioPickAudio(): String = text(R.string.studio_pick_audio)
    fun studioPickImage(): String = text(R.string.studio_pick_image)
    fun archiveTitle(): String = text(R.string.archive_title)
    fun archiveDesc(): String = text(R.string.archive_desc)
    fun archivePickFiles(): String = text(R.string.archive_pick_files)
    fun archiveCreate(): String = text(R.string.archive_create)
    fun archiveExtract(): String = text(R.string.archive_extract)
    fun archiveCreated(): String = text(R.string.archive_created)
    fun archiveExtracted(): String = text(R.string.archive_extracted)
    fun archiveSevenZipNote(): String = text(R.string.archive_seven_zip_note)
    fun pdfSplitTitle(): String = text(R.string.pdf_split_title)
    fun pdfSplitDesc(): String = text(R.string.pdf_split_desc)
    fun pdfSplitRangeLabel(): String = text(R.string.pdf_split_range_label)
    fun pdfSplitAction(): String = text(R.string.pdf_split_action)
    fun pdfSplitDone(): String = text(R.string.pdf_split_done)
    fun pdfSplitInvalid(): String = text(R.string.pdf_split_invalid)
    fun pdfCreateTitle(): String = text(R.string.pdf_create_title)
    fun pdfCreateDesc(): String = text(R.string.pdf_create_desc)
    fun pdfCreatePick(): String = text(R.string.pdf_create_pick)
    fun pdfCreateAction(): String = text(R.string.pdf_create_action)
    fun pdfCreateDone(): String = text(R.string.pdf_create_done)
    fun epubTitle(): String = text(R.string.epub_title)
    fun epubDesc(): String = text(R.string.epub_desc)
    fun epubPick(): String = text(R.string.epub_pick)
    fun epubCreateAction(): String = text(R.string.epub_create_action)
    fun epubExtractAction(): String = text(R.string.epub_extract_action)
    fun epubCreated(): String = text(R.string.epub_created)
    fun epubExtracted(): String = text(R.string.epub_extracted)
    fun crashReportingTitle(): String = text(R.string.crash_reporting_title)
    fun crashReportingDesc(): String = text(R.string.crash_reporting_desc)
    fun crashReportingEnable(): String = text(R.string.crash_reporting_enable)
    fun crashReportingShare(): String = text(R.string.crash_reporting_share)
    fun crashReportingDelete(): String = text(R.string.crash_reporting_delete)
    fun crashReportingNone(): String = text(R.string.crash_reporting_none)
    fun crashReportingCount(count: Int): String = text(R.string.crash_reporting_count, count)
    fun folderWatchTitle(): String = text(R.string.folder_watch_title)
    fun folderWatchDesc(): String = text(R.string.folder_watch_desc)
    fun folderWatchTargetLabel(): String = text(R.string.folder_watch_target_label)
    fun folderWatchStart(): String = text(R.string.folder_watch_start)
    fun folderWatchStop(): String = text(R.string.folder_watch_stop)
    fun folderWatchActive(): String = text(R.string.folder_watch_active)
    fun folderWatchFound(name: String): String = text(R.string.folder_watch_found, name)
    fun splitSilenceTitle(): String = text(R.string.split_silence_title)
    fun splitSilenceDesc(): String = text(R.string.split_silence_desc)
    fun splitSilenceRun(): String = text(R.string.split_silence_run)
    fun splitSilenceDone(count: Int): String = text(R.string.split_silence_done, count)
    fun cutTranscriptTitle(): String = text(R.string.cut_transcript_title)
    fun cutTranscriptDesc(): String = text(R.string.cut_transcript_desc)
    fun cutTranscriptApply(): String = text(R.string.cut_transcript_apply)
    fun cutTranscriptTranscribe(): String = text(R.string.cut_transcript_transcribe)
    fun cutTranscriptTranscribed(count: Int): String =
        text(R.string.cut_transcript_transcribed, count)
    fun cutTranscriptTapHint(): String = text(R.string.cut_transcript_tap_hint)
    fun cutTranscriptDeletedCount(count: Int): String =
        text(R.string.cut_transcript_deleted_count, count)
    fun privacyScrubAudio(): String = text(R.string.privacy_scrub_audio)

    fun transcribeEngineUnavailable(): String = text(R.string.transcribe_engine_unavailable)
    fun transcribeManualHint(): String = text(R.string.transcribe_manual_hint)
    fun transcribeDownloading(percent: Int): String = text(R.string.transcribe_downloading, percent)
    fun transcribeExportFailed(): String = text(R.string.transcribe_export_failed)
    fun stemsEngineUnavailable(): String = text(R.string.stems_engine_unavailable)
    fun stemsApproximateNote(): String = text(R.string.stems_approximate_note)
    fun stemsRunOffline(): String = text(R.string.stems_run_offline)
    fun stemsDone(count: Int): String = text(R.string.stems_done, count)
    fun stemsDoneApproximate(count: Int): String = text(R.string.stems_done_approximate, count)
    fun recipesRun(): String = text(R.string.recipes_run)
    fun recipesRan(): String = text(R.string.recipes_ran)
    fun recipesNoSteps(): String = text(R.string.recipes_no_steps)
    fun pluginsApply(): String = text(R.string.plugins_apply)
    fun pluginsApplied(): String = text(R.string.plugins_applied)
    fun folderWatchConvert(): String = text(R.string.folder_watch_convert)
    fun folderWatchConvertDesc(): String = text(R.string.folder_watch_convert_desc)
    fun folderWatchQueued(name: String): String = text(R.string.folder_watch_queued, name)
    fun burnSubsTitle(): String = text(R.string.burn_subs_title)
    fun burnSubsDesc(): String = text(R.string.burn_subs_desc)
    fun burnSubsPickVideo(): String = text(R.string.burn_subs_pick_video)
    fun burnSubsPickSubtitle(): String = text(R.string.burn_subs_pick_subtitle)
    fun burnSubsRun(): String = text(R.string.burn_subs_run)
    fun burnSubsDone(): String = text(R.string.burn_subs_done)
    fun chapterSplitTitle(): String = text(R.string.chapter_split_title)
    fun chapterSplitDesc(): String = text(R.string.chapter_split_desc)
    fun chapterSplitRun(): String = text(R.string.chapter_split_run)
    fun chapterSplitDone(count: Int): String = text(R.string.chapter_split_done, count)
    fun chapterSplitNone(): String = text(R.string.chapter_split_none)
    fun chapterSplitUseEmbedded(): String = text(R.string.chapter_split_use_embedded)
    fun queuePaused(): String = text(R.string.queue_paused)
    fun queueTitle(): String = text(R.string.queue_title)
    fun queuePause(): String = text(R.string.queue_pause)
    fun queueResume(): String = text(R.string.queue_resume)
    fun queueRetry(): String = text(R.string.queue_retry)
    fun queueMoveUp(): String = text(R.string.queue_move_up)
    fun queueMoveDown(): String = text(R.string.queue_move_down)

    fun studioSaved(): String = text(R.string.studio_saved)

    fun studioFailed(): String = text(R.string.studio_failed)

    fun studioModelReady(): String = text(R.string.studio_model_ready)

    fun studioNeedsFile(): String = text(R.string.studio_needs_file)

    fun studioPlay(): String = text(R.string.studio_play)

    fun studioStop(): String = text(R.string.studio_stop)

    fun studioGuardEnabled(): String = text(R.string.studio_guard_enabled)

    fun metadataKindLabel(kind: MetadataTargetKind): String {
        return when (kind) {
            MetadataTargetKind.Image -> text(R.string.text_metadata_kind_label)
            MetadataTargetKind.Video -> text(R.string.text_metadata_kind_label_video)
        }
    }

    fun metadataSupportLabel(inspection: MetadataInspection): String {
        return when {
            inspection.kind == MetadataTargetKind.Video -> text(R.string.text_metadata_support_label)
            !inspection.editable -> text(R.string.text_metadata_support_label_unsupported_cleanup)
            !inspection.canWrite -> text(R.string.text_metadata_support_label_read_only)
            inspection.hasRemovableMetadata -> text(R.string.text_metadata_support_label_metadata_found)
            else -> text(R.string.text_metadata_support_label_clean)
        }
    }

    fun metadataLabel(value: String): String {
        return when (value) {
            "Format" -> text(R.string.text_metadata_label)
            "Size" -> text(R.string.text_metadata_label_size)
            "Dimensions" -> text(R.string.text_metadata_label_dimensions)
            "Duration" -> text(R.string.text_metadata_label_duration)
            "Frame rate" -> frameRate
            "Overall bitrate" -> text(R.string.text_metadata_label_overall_bitrate)
            "GPS" -> metadataGps
            "Captured" -> text(R.string.text_metadata_label_captured)
            "Camera" -> text(R.string.text_metadata_label_camera)
            "Software" -> text(R.string.text_metadata_label_software)
            "Orientation" -> text(R.string.text_metadata_label_orientation)
            "Description" -> text(R.string.text_metadata_label_description)
            "Artist" -> text(R.string.text_metadata_label_artist)
            "Copyright" -> text(R.string.text_metadata_label_copyright)
            "EXIF" -> "EXIF"
            "XMP" -> "XMP"
            "IPTC" -> "IPTC"
            "Comment" -> text(R.string.text_metadata_label_comment)
            "Removable" -> text(R.string.text_metadata_label_removable)
            "Backups" -> text(R.string.text_metadata_label_backups)
            "Core hash" -> text(R.string.text_metadata_label_core_hash)
            "Support" -> text(R.string.text_metadata_label_support)
            else -> value
        }
    }

    fun yesNo(value: Boolean): String {
        return if (value) text(R.string.text_yes_no_1) else text(R.string.text_yes_no_2)
    }

    fun yesNoLabel(value: Boolean, label: String): String {
        return text(R.string.format_label_value, label, yesNo(value))
    }

    fun metadataBackupCountLabel(count: Int): String {
        return quantity(R.plurals.count_metadata_backup_count_label, count, count)
    }

    fun metadataSegmentCountLabel(count: Int): String {
        return quantity(R.plurals.count_metadata_segment_count_label, count, count)
    }

    fun metadataMessage(message: MetadataStatusMessage): String {
        val base = when (message.key) {
            MetadataMessageKey.Cleaned -> text(R.string.text_metadata_message)
            MetadataMessageKey.Restored -> text(R.string.text_metadata_message_metadata_restored)
            MetadataMessageKey.NoRemovableMetadata -> text(R.string.text_metadata_message_no_removable_metadata_was_found)
            MetadataMessageKey.UnsupportedImageFormat -> text(R.string.text_metadata_message_lossless_cleanup_currently_supports_jpg_jpeg_jfif_only)
            MetadataMessageKey.WritePermissionNeeded -> text(R.string.text_metadata_message_this_file_did_not_grant_write_access)
            MetadataMessageKey.CouldNotRead -> text(R.string.text_metadata_message_could_not_read_metadata)
            MetadataMessageKey.CouldNotWrite -> text(R.string.text_metadata_message_could_not_write_metadata_changes)
            MetadataMessageKey.BackupMissing -> text(R.string.text_metadata_message_metadata_backup_was_not_found)
            MetadataMessageKey.BackupDoesNotMatch -> text(R.string.text_metadata_message_this_backup_does_not_match_the_selected_image)
            MetadataMessageKey.InvalidJpeg -> text(R.string.text_metadata_message_this_jpeg_file_could_not_be_parsed_safely)
        }
        val detail = message.detail?.resolve(context)
        return if (detail.isNullOrBlank()) base else text(R.string.format_label_value, base, detail)
    }

    fun metadataOrientationLabel(value: Int?): String {
        return when (value) {
            ExifInterface.ORIENTATION_NORMAL -> text(R.string.text_metadata_orientation_label)
            ExifInterface.ORIENTATION_ROTATE_90 -> text(R.string.text_metadata_orientation_label_rotate_90)
            ExifInterface.ORIENTATION_ROTATE_180 -> text(R.string.text_metadata_orientation_label_rotate_180)
            ExifInterface.ORIENTATION_ROTATE_270 -> text(R.string.text_metadata_orientation_label_rotate_270)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> text(R.string.text_metadata_orientation_label_flipped)
            else -> unknownType
        }
    }

    fun metadataBackupLabel(
        backup: MetadataBackupInfo,
        recommended: Boolean
    ): String {
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(backup.createdAtMillis))
        val size = formatBytes(backup.segmentBytes, this)
        val suffix = if (recommended) {
            text(R.string.text_metadata_backup_label)
        } else {
            ""
        }
        return "$time · ${metadataSegmentCountLabel(backup.segmentCount)} · $size$suffix"
    }

    fun qrCodeFor(value: String): String {
        return text(R.string.text_qr_code_for, value)
    }

    fun pdfPasswordMessage(fileName: String): String {
        return text(R.string.text_pdf_password_message, fileName)
    }

    fun pdfOutputPasswordMessage(fileCount: Int): String {
        return quantity(R.plurals.pdf_output_password_message, fileCount, fileCount)
    }

    fun summaryMessage(value: LocalizedText): String {
        val resolved = value.resolve(context)
        return if (value is LocalizedText.Resource && value.id in setOf(R.string.task_processing, R.string.task_saving)) {
            runningKeepAwakeMessage(resolved)
        } else resolved
    }

    private fun runningKeepAwakeMessage(action: String): String {
        return text(R.string.text_running_keep_awake_message, action)
    }

    fun taskMessage(value: LocalizedText): String = value.resolve(context)


    fun progressLabel(progress: TaskProgress?): String {
        if (progress == null) return waiting
        return when (progress.status) {
            TaskProgressStatus.Queued -> waiting
            TaskProgressStatus.Running -> text(R.string.format_task_progress, processing, (progress.progress * 100).toInt())
            TaskProgressStatus.Completed -> flowComplete
            TaskProgressStatus.Cancelled -> cancelled
            TaskProgressStatus.Failed -> failed
        }
    }

    fun categoryLabel(category: FileCategory): String {
        return when (category) {
            FileCategory.Video -> optionValue("Video")
            FileCategory.Audio -> optionValue("Audio")
            FileCategory.Image -> optionValue("Image")
            FileCategory.Pdf -> optionValue("PDF")
            FileCategory.Document -> optionValue("Document")
            FileCategory.Font -> optionValue("Font")
            FileCategory.Subtitle -> optionValue("Subtitle")
        }
    }

    fun toFormat(format: String): String = text(R.string.format_to_target, format)

    fun accentLabel(option: AccentColorOption): String = optionValue(option.englishLabel)

    fun themeModeLabel(option: ThemeModeOption): String {
        return when (option) {
            ThemeModeOption.System -> text(R.string.text_theme_mode_label)
            ThemeModeOption.Light -> text(R.string.text_theme_mode_label_light)
            ThemeModeOption.Dark -> text(R.string.text_theme_mode_label_dark)
        }
    }

    val usePureBlackTheme: String get() = text(R.string.text_use_pure_black_theme)

    val settingsTitle: String get() = text(R.string.ui_settings_title)
    val settingsGroupGeneral: String get() = text(R.string.ui_settings_group_general)
    val settingsGroupAppearance: String get() = text(R.string.ui_settings_group_appearance)
    val settingsGroupPrivacyEngines: String get() = text(R.string.ui_settings_group_privacy_engines)
    val settingsGroupAboutSupport: String get() = text(R.string.ui_settings_group_about_support)
    val settingsLanguage: String get() = text(R.string.ui_settings_language)
    val settingsOfflineEngines: String get() = text(R.string.ui_settings_offline_engines)
    val settingsOfflineEnginesDesc: String get() = text(R.string.ui_settings_offline_engines_desc)
    val settingsMetadataSecurityDesc: String get() = text(R.string.ui_settings_metadata_security_desc)
    val settingsCheckUpdates: String get() = text(R.string.ui_settings_check_updates)
    val settingsQuickPrivacyCapsule: String get() = text(R.string.ui_settings_quick_privacy_capsule)
    val settingsBack: String get() = text(R.string.ui_settings_back)

    fun languageLabel(option: LanguageOption): String =
        if (option.tag.isEmpty()) text(R.string.language_follow_system) else option.nativeName


    fun updateChannelLabel(channel: UpdateChannel): String {
        return when (channel) {
            UpdateChannel.Stable -> stableUpdateChannel
            UpdateChannel.Preview -> previewUpdateChannel
        }
    }

    fun currentIsLatest(channel: UpdateChannel): String {
        return text(R.string.text_current_is_latest, updateChannelLabel(channel))
    }

    fun updateAvailableMessage(release: UpdateRelease): String {
        return text(R.string.text_update_available_message, release.versionName)
    }

    fun releaseDetail(release: UpdateRelease): String {
        return text(R.string.text_release_detail, release.assetName, formatBytes(release.sizeBytes, this))
    }

    fun downloadProgressMessage(progress: DownloadProgress): String {
        val downloaded = formatBytes(progress.bytesDownloaded, this)
        val total = progress.totalBytes
        return if (total == null) {
            downloaded
        } else {
            "$downloaded / ${formatBytes(total, this)}"
        }
    }

    fun downloadFailureMessage(detail: LocalizedText?): String = detail?.resolve(context) ?: downloadFailed


    fun updateFailureMessage(reason: UpdateFailureReason, detail: LocalizedText?): String {
        val resolvedDetail = detail?.resolve(context)
        val base = when (reason) {
            UpdateFailureReason.Network -> text(R.string.text_update_failure_message)
            UpdateFailureReason.NoRelease -> text(R.string.text_update_failure_message_no_release_found_for_this_channel)
            UpdateFailureReason.NoApkAsset -> text(R.string.text_update_failure_message_the_release_has_no_android_apk)
            UpdateFailureReason.MissingVersionMetadata -> text(R.string.text_update_failure_message_the_release_is_missing_android_version_metadata)
            UpdateFailureReason.InvalidResponse -> text(R.string.text_update_failure_message_github_returned_an_unreadable_response)
        }
        return if (resolvedDetail.isNullOrBlank()) base else text(R.string.format_label_value, base, resolvedDetail)
    }

    fun optionValue(value: String): String {
        return when (value) {
            "Video" -> text(R.string.text_option_value)
            "Close" -> text(R.string.text_option_value_close)
            "Audio" -> text(R.string.text_option_value_audio)
            "Image" -> text(R.string.text_option_value_image)
            "Document" -> text(R.string.text_option_value_document)
            "Compatibility" -> text(R.string.text_option_value_multi_format)
            "Re-encode" -> text(R.string.text_option_value_re_encode)
            "30s GIF" -> text(R.string.text_option_value_up_to_30s_gif)
            "Summary Sheet" -> text(R.string.text_option_value_summary_sheet)
            "contact_sheet_jpg" -> text(R.string.text_option_value_contact_sheet_jpg)
            "contact_sheet_png" -> text(R.string.text_option_value_contact_sheet_png)
            "3 × 4 (12)" -> text(R.string.text_option_value_3_4_12_frames)
            "3 × 3 (9)" -> text(R.string.text_option_value_3_3_9_frames)
            "4 × 4 (16)" -> text(R.string.text_option_value_4_4_16_frames)
            "5 × 5 (25)" -> text(R.string.text_option_value_5_5_25_frames)
            "Auto engine" -> text(R.string.text_option_value_native_or_compatibility)
            "PDF" -> "PDF"
            "TXT" -> "TXT"
            "MD" -> "MD"
            "Page rasterization" -> text(R.string.text_option_value_page_rasterization)
            "Merge PDFs" -> text(R.string.text_option_value_merge_pdfs)
            "Text layer" -> text(R.string.text_option_value_text_layer)
            "Markdown" -> text(R.string.text_option_value_markdown)
            "pdf_encrypt" -> text(R.string.text_option_value_encrypt_pdf)
            "pdf_decrypt" -> text(R.string.text_option_value_decrypt_pdf)
            "pdf_compress" -> text(R.string.text_option_value_compress_pdf)
            "Reduce file size" -> text(R.string.text_option_value_reduce_file_size)
            "High quality" -> text(R.string.text_option_value_high_quality)
            "Small file" -> text(R.string.text_option_value_small_file)
            "Password protect" -> text(R.string.text_option_value_password_protect)
            "Remove password" -> text(R.string.text_option_value_remove_password)
            "Office to PDF" -> text(R.string.text_option_value_office_to_pdf)
            "Batch" -> text(R.string.text_option_value_batch_processing)
            "Supports transparency" -> text(R.string.text_option_value_supports_transparency)
            "Lossless output" -> text(R.string.text_option_value_lossless_output)
            BATCH_MIXED_OPTION -> text(R.string.text_option_value_mixed)
            "High" -> text(R.string.text_option_value_high)
            "Balanced" -> text(R.string.text_option_value_balanced)
            "Small" -> text(R.string.text_option_value_small)
            "Original" -> text(R.string.text_option_value_original)
            AUDIO_BIT_DEPTH_ORIGINAL -> text(R.string.text_option_value_bit_depth_original)
            AUDIO_BIT_DEPTH_16 -> text(R.string.text_option_value_bit_depth_16)
            AUDIO_BIT_DEPTH_24 -> text(R.string.text_option_value_bit_depth_24)
            AUDIO_BIT_DEPTH_32 -> text(R.string.text_option_value_bit_depth_32)
            GIF_FRAME_RATE_15 -> text(R.string.text_option_value_gif_fps_15)
            GIF_FRAME_RATE_20 -> text(R.string.text_option_value_gif_fps_20)
            GIF_FRAME_RATE_25 -> text(R.string.text_option_value_gif_fps_25)
            GIF_FRAME_RATE_30 -> text(R.string.text_option_value_gif_fps_30)
            GIF_COLORS_256 -> text(R.string.text_option_value_gif_colors_256)
            GIF_COLORS_128 -> text(R.string.text_option_value_gif_colors_128)
            GIF_COLORS_64 -> text(R.string.text_option_value_gif_colors_64)
            GIF_COLORS_32 -> text(R.string.text_option_value_gif_colors_32)
            ADVANCED_FADE_OFF -> text(R.string.text_option_value_off)
            ADVANCED_FADE_HALF_SECOND,
            ADVANCED_FADE_ONE_SECOND,
            ADVANCED_FADE_TWO_SECONDS,
            AUDIO_VOLUME_50,
            AUDIO_VOLUME_100,
            AUDIO_VOLUME_150,
            AUDIO_VOLUME_200 -> value
            VIDEO_MIRROR_OFF -> text(R.string.text_option_value_off)
            VIDEO_MIRROR_HORIZONTAL -> text(R.string.text_option_value_horizontal)
            VIDEO_MIRROR_VERTICAL -> text(R.string.text_option_value_vertical)
            VIDEO_MIRROR_BOTH -> text(R.string.text_option_value_both)
            VIDEO_ROTATION_NONE -> text(R.string.text_option_value_none)
            VIDEO_ROTATION_90_CW -> text(R.string.text_option_value_90_clockwise)
            VIDEO_ROTATION_90_CCW -> text(R.string.text_option_value_90_counterclockwise)
            VIDEO_ROTATION_180 -> text(R.string.text_option_value_180)
            VIDEO_ASPECT_KEEP -> text(R.string.text_option_value_keep)
            VIDEO_ASPECT_FIT_16_9 -> text(R.string.text_option_value_fit_16_9)
            VIDEO_ASPECT_FIT_9_16 -> text(R.string.text_option_value_fit_9_16)
            VIDEO_ASPECT_FIT_1_1 -> text(R.string.text_option_value_fit_1_1)
            VIDEO_ASPECT_CROP_16_9 -> text(R.string.text_option_value_crop_16_9)
            VIDEO_ASPECT_CROP_9_16 -> text(R.string.text_option_value_crop_9_16)
            VIDEO_ASPECT_CROP_1_1 -> text(R.string.text_option_value_crop_1_1)
            VIDEO_MOTION_BLUR_OFF -> text(R.string.text_option_value_off)
            VIDEO_MOTION_BLUR_SUBTLE -> text(R.string.text_option_value_subtle_motion_blur)
            VIDEO_MOTION_BLUR_STANDARD -> text(R.string.text_option_value_standard_motion_blur)
            VIDEO_MOTION_BLUR_HEAVY -> text(R.string.text_option_value_heavy_motion_blur)
            AUDIO_VOLUME_MUTE -> text(R.string.text_option_value_mute)
            AUDIO_ECHO_OFF -> text(R.string.text_option_value_off)
            AUDIO_ECHO_LIGHT -> text(R.string.text_option_value_light)
            AUDIO_ECHO_ROOM -> text(R.string.text_option_value_room)
            AUDIO_DENOISE_OFF -> text(R.string.text_option_value_off)
            AUDIO_DENOISE_LIGHT -> text(R.string.text_option_value_light_variant_2)
            AUDIO_DENOISE_STANDARD -> text(R.string.text_option_value_standard)
            VIDEO_COMPRESSION_STANDARD -> text(R.string.text_option_value_off_manual)
            VIDEO_INTERPOLATION_OFF -> text(R.string.text_option_value_off)
            VIDEO_INTERPOLATION_OPTICAL_FLOW_2X -> text(R.string.text_option_value_optical_flow_2_interpolation)
            VIDEO_INTERPOLATION_RIFE_2X -> text(R.string.text_option_value_rife_2_interpolation)
            VIDEO_COMPRESSION_VISUAL_LOSSLESS -> text(R.string.text_option_value_visual_lossless)
            VIDEO_COMPRESSION_BALANCED -> text(R.string.text_option_value_balanced_shrink)
            VIDEO_COMPRESSION_SMALL -> text(R.string.text_option_value_small_file)
            IMAGE_SUPER_RESOLUTION_OFF -> text(R.string.text_option_value_off)
            IMAGE_SUPER_RESOLUTION_2X -> text(R.string.text_option_value_2_bilinear)
            IMAGE_SUPER_RESOLUTION_3X -> text(R.string.text_option_value_3_bilinear)
            IMAGE_SUPER_RESOLUTION_4X -> text(R.string.text_option_value_4_bilinear)
            IMAGE_SUPER_RESOLUTION_AI_ANIME -> text(R.string.text_option_value_real_esrgan_anime_4_ai)
            IMAGE_SUPER_RESOLUTION_AI -> text(R.string.text_option_value_real_esrgan_4_ai)
            IMAGE_RESIZE_ORIGINAL -> text(R.string.text_option_value_resize_original)
            IMAGE_RESIZE_4096 -> text(R.string.text_option_value_resize_long_edge_4096)
            IMAGE_RESIZE_2560 -> text(R.string.text_option_value_resize_long_edge_2560)
            IMAGE_RESIZE_1920 -> text(R.string.text_option_value_resize_long_edge_1920)
            IMAGE_RESIZE_1280 -> text(R.string.text_option_value_resize_long_edge_1280)
            IMAGE_RESIZE_1080 -> text(R.string.text_option_value_resize_long_edge_1080)
            IMAGE_RESIZE_720 -> text(R.string.text_option_value_resize_long_edge_720)
            "Auto bitrate" -> text(R.string.text_option_value_auto_recommended)
            "Auto audio bitrate" -> text(R.string.text_option_value_auto_encoder_default)
            "Recommended audio bitrate" -> text(R.string.text_option_value_recommended_192_kbps)
            "High audio bitrate" -> text(R.string.text_option_value_high_256_kbps)
            "Compact audio bitrate" -> text(R.string.text_option_value_compact_128_kbps)
            "Voice audio bitrate" -> text(R.string.text_option_value_voice_96_kbps)
            "Constant bitrate (CBR)" -> text(R.string.text_option_value_constant_bitrate_cbr)
            "Variable bitrate (VBR)" -> text(R.string.text_option_value_variable_bitrate_vbr)
            "V0 (highest quality)" -> text(R.string.text_option_value_mp3_vbr_v0)
            "V2 (recommended)" -> text(R.string.text_option_value_mp3_vbr_v2)
            "V4 (balanced)" -> text(R.string.text_option_value_mp3_vbr_v4)
            "V6 (smaller file)" -> text(R.string.text_option_value_mp3_vbr_v6)
            "Recommended sample rate" -> text(R.string.text_option_value_recommended_48_khz)
            "Low bitrate" -> text(R.string.text_option_value_low_1_mbps)
            "Medium bitrate" -> text(R.string.text_option_value_medium_2_5_mbps)
            "High bitrate" -> text(R.string.text_option_value_high_5_mbps)
            "Very high bitrate" -> text(R.string.text_option_value_very_high_8_mbps)
            "Ultra bitrate" -> text(R.string.text_option_value_ultra_16_mbps)
            "H.264", "H.265" -> value
            "Frame rate 25" -> text(R.string.text_option_value_max_25_fps)
            "Frame rate 30" -> text(R.string.text_option_value_max_30_fps)
            "Frame rate 60" -> text(R.string.text_option_value_max_60_fps)
            "Auto" -> text(R.string.text_option_value_auto)
            "Keep original picture" -> text(R.string.text_option_value_keep_original_picture)
            "Auto allocation" -> text(R.string.text_option_value_auto_allocation)
            "Medium" -> text(R.string.text_option_value_medium)
            "Low" -> text(R.string.text_option_value_low)
            "Stereo" -> text(R.string.text_option_value_stereo)
            "Mono" -> text(R.string.text_option_value_mono)
            "Keep if possible" -> text(R.string.text_option_value_keep_if_possible)
            "Flatten" -> text(R.string.text_option_value_flatten)
            "One file per input" -> text(R.string.text_option_value_one_file_per_input)
            "Single PDF" -> text(R.string.text_option_value_single_pdf)
            "One PDF per image" -> text(R.string.text_option_value_one_pdf_per_image)
            "First frame" -> text(R.string.text_option_value_first_frame)
            "Split frames" -> text(R.string.text_option_value_split_frames)
            "All frames in one PDF" -> text(R.string.text_option_value_all_frames_in_one_pdf)
            "One PDF per frame" -> text(R.string.text_option_value_one_pdf_per_frame)
            "A4 fit" -> text(R.string.text_option_value_a4_fit)
            "Original ratio" -> text(R.string.text_option_value_original_ratio)
            "Low resolution" -> text(R.string.text_option_value_low_resolution)
            "High detail" -> text(R.string.text_option_value_high_detail)
            "Material You" -> text(R.string.text_option_value_material_you)
            "Charcoal" -> text(R.string.text_option_value_charcoal)
            "Deep Navy" -> text(R.string.text_option_value_deep_navy)
            "Forest Green" -> text(R.string.text_option_value_forest_green)
            "Steel Blue" -> text(R.string.text_option_value_steel_blue)
            "Dusty Rose" -> text(R.string.text_option_value_dusty_rose)
            "Mustard" -> text(R.string.text_option_value_mustard)
            "Burnt Orange" -> text(R.string.text_option_value_burnt_orange)
            "Electric Blue" -> text(R.string.text_option_value_electric_blue)
            "Fern Green" -> text(R.string.text_option_value_fern_green)
            "Deep Purple" -> text(R.string.text_option_value_deep_purple)
            "Font" -> text(R.string.text_option_value_font)
            "Web font" -> text(R.string.text_option_value_web_font)
            "Uncompressed" -> text(R.string.text_option_value_uncompressed)
            "Subtitle" -> text(R.string.text_option_value_subtitle)
            "SRT" -> "SRT"
            "VTT" -> "VTT"
            "LRC" -> "LRC"
            "ASS" -> "ASS"
            "Lyrics" -> text(R.string.text_option_value_lyrics)
            "Styled subtitle" -> text(R.string.text_option_value_styled_subtitle)
            else -> value
        }
    }

    // Mastering --------------------------------------------------------------
    fun studioSectionMastering(): String = text(R.string.studio_section_mastering)
    fun studioMastering(): String = text(R.string.studio_mastering)
    fun studioMasteringDesc(): String = text(R.string.studio_mastering_desc)
    fun studioStemBalance(): String = text(R.string.studio_stem_balance)
    fun studioStemBalanceDesc(): String = text(R.string.studio_stem_balance_desc)
    fun studioEq(): String = text(R.string.studio_eq)
    fun studioEqDesc(): String = text(R.string.studio_eq_desc)
    fun studioSpectrum(): String = text(R.string.studio_spectrum)
    fun studioSpectrumDesc(): String = text(R.string.studio_spectrum_desc)
    fun studioLoudness(): String = text(R.string.studio_loudness)
    fun studioLoudnessDesc(): String = text(R.string.studio_loudness_desc)
    fun studioRadioReady(): String = text(R.string.studio_radio_ready)
    fun studioRadioReadyDesc(): String = text(R.string.studio_radio_ready_desc)
    fun studioLoudnessNorm(): String = text(R.string.studio_loudness_norm)
    fun studioLoudnessNormDesc(): String = text(R.string.studio_loudness_norm_desc)
    fun studioExciter(): String = text(R.string.studio_exciter)
    fun studioExciterDesc(): String = text(R.string.studio_exciter_desc)

    fun masteringTitle(): String = text(R.string.mastering_title)
    fun masteringLoad(): String = text(R.string.mastering_load)
    fun masteringEmpty(): String = text(R.string.mastering_empty)
    fun masteringStart(): String = text(R.string.mastering_start)
    fun masteringStop(): String = text(R.string.mastering_stop)
    fun masteringReset(): String = text(R.string.mastering_reset)
    fun masteringTarget(): String = text(R.string.mastering_target)
    fun masteringRunning(): String = text(R.string.mastering_running)
    fun masteringAnalyzing(): String = text(R.string.mastering_analyzing)
    fun masteringSeparating(): String = text(R.string.mastering_separating)
    fun masteringBalancing(): String = text(R.string.mastering_balancing)
    fun masteringEqStep(): String = text(R.string.mastering_eq)
    fun masteringDynamicsStep(): String = text(R.string.mastering_dynamics)
    fun masteringLoudnessStep(): String = text(R.string.mastering_loudness_step)
    fun masteringDone(lufs: String, truePeak: String): String =
        text(R.string.mastering_done, lufs, truePeak)
    fun masteringFailed(): String = text(R.string.mastering_failed)
    fun masteringStopped(): String = text(R.string.mastering_stopped)
    fun masteringExport(): String = text(R.string.mastering_export)
    fun masteringExported(): String = text(R.string.mastering_exported)
    fun masteringAb(): String = text(R.string.mastering_ab)
    fun masteringShowCommand(): String = text(R.string.mastering_show_command)
    fun masteringHideCommand(): String = text(R.string.mastering_hide_command)
    fun masteringStepUpload(): String = text(R.string.mastering_step_upload)
    fun masteringStepUploadDesc(): String = text(R.string.mastering_step_upload_desc)
    fun masteringStepStems(): String = text(R.string.mastering_step_stems)
    fun masteringStepStemsDesc(): String = text(R.string.mastering_step_stems_desc)
    fun masteringStepBalance(): String = text(R.string.mastering_step_balance)
    fun masteringStepBalanceDesc(): String = text(R.string.mastering_step_balance_desc)
    fun masteringStepEq(): String = text(R.string.mastering_step_eq)
    fun masteringStepEqDesc(): String = text(R.string.mastering_step_eq_desc)
    fun masteringStepDynamics(): String = text(R.string.mastering_step_dynamics)
    fun masteringStepDynamicsDesc(): String = text(R.string.mastering_step_dynamics_desc)
    fun masteringStepLoudness(): String = text(R.string.mastering_step_loudness)
    fun masteringStepLoudnessDesc(): String = text(R.string.mastering_step_loudness_desc)
    fun masteringStemsTitle(): String = text(R.string.mastering_stems_title)
    fun masteringStemsDesc(): String = text(R.string.mastering_stems_desc)
    fun masteringStemsEmpty(): String = text(R.string.mastering_stems_empty)
    fun masteringStemsRun(): String = text(R.string.mastering_stems_run)
    fun masteringAutoBalance(): String = text(R.string.mastering_auto_balance)
    fun masteringResetBalance(): String = text(R.string.mastering_reset_balance)
    fun masteringBalanceNote(): String = text(R.string.mastering_balance_note)
    fun masteringSpectrumTitle(): String = text(R.string.mastering_spectrum_title)
    fun masteringSpectrumAnalyze(): String = text(R.string.mastering_spectrum_analyze)
    fun masteringSpectrumFlatten(): String = text(R.string.mastering_spectrum_flatten)
    fun masteringSpectrumBalanced(): String = text(R.string.mastering_spectrum_balanced)
    fun masteringSpectrumEmpty(): String = text(R.string.mastering_spectrum_empty)
    fun masteringSpectrumLegendAudio(): String = text(R.string.mastering_spectrum_legend_audio)
    fun masteringSpectrumLegendReference(): String = text(R.string.mastering_spectrum_legend_reference)
    fun masteringSpectrumLegendPeak(): String = text(R.string.mastering_spectrum_legend_peak)
    fun masteringEqTitle(): String = text(R.string.mastering_eq_title)
    fun masteringEqDesc(): String = text(R.string.mastering_eq_desc)
    fun masteringEqAuto(): String = text(R.string.mastering_eq_auto)
    fun masteringEqFlat(): String = text(R.string.mastering_eq_flat)
    fun masteringEqGain(value: String): String = text(R.string.mastering_eq_gain, value)
    fun masteringEqQ(value: String): String = text(R.string.mastering_eq_q, value)
    fun masteringLoudnessTitle(): String = text(R.string.mastering_loudness_title)
    fun masteringLoudnessIntegrated(): String = text(R.string.mastering_loudness_integrated)
    fun masteringLoudnessIntegratedHint(value: String): String =
        text(R.string.mastering_loudness_integrated_hint, value)
    fun masteringLoudnessTruePeak(): String = text(R.string.mastering_loudness_true_peak)
    fun masteringLoudnessTruePeakHint(value: String): String =
        text(R.string.mastering_loudness_true_peak_hint, value)
    fun masteringLoudnessRange(): String = text(R.string.mastering_loudness_range)
    fun masteringLoudnessRangeHint(): String = text(R.string.mastering_loudness_range_hint)
    fun masteringLoudnessDynamics(): String = text(R.string.mastering_loudness_dynamics)
    fun masteringLoudnessDynamicsHint(): String = text(R.string.mastering_loudness_dynamics_hint)
    fun masteringLoudnessPlaceholder(): String = text(R.string.mastering_loudness_placeholder)
    fun masteringCeilingLabel(value: String): String = text(R.string.mastering_ceiling_label, value)
    fun masteringLoudnessCeiling(value: String): String = text(R.string.mastering_loudness_ceiling, value)
    fun masteringClipGuard(): String = text(R.string.mastering_clip_guard)
    fun masteringAdaptiveLimiting(): String = text(R.string.mastering_adaptive_limiting)
    fun masteringDeEsser(): String = text(R.string.mastering_de_esser)
    fun masteringExciterSwitch(): String = text(R.string.mastering_exciter_switch)
    fun masteringMonoCheck(): String = text(R.string.mastering_mono_check)
    fun masteringShapeTitle(): String = text(R.string.mastering_shape_title)
    fun masteringShapeDesc(): String = text(R.string.mastering_shape_desc)
    fun masteringCheckTitle(): String = text(R.string.mastering_check_title)
    fun masteringCheckLoudness(): String = text(R.string.mastering_check_loudness)
    fun masteringCheckTruePeak(): String = text(R.string.mastering_check_true_peak)
    fun masteringCheckRange(): String = text(R.string.mastering_check_range)
    fun masteringCheckClipping(): String = text(R.string.mastering_check_clipping)
    fun masteringCheckPhase(): String = text(R.string.mastering_check_phase)
    fun masteringCheckSampleRate(): String = text(R.string.mastering_check_sample_rate)
    fun masteringCheckClippingYes(): String = text(R.string.mastering_check_clipping_yes)
    fun masteringCheckClippingNo(): String = text(R.string.mastering_check_clipping_no)
    fun masteringCheckMonoSafe(): String = text(R.string.mastering_check_mono_safe)
    fun masteringCheckKept(value: String): String = text(R.string.mastering_check_kept, value)
    fun masteringCheckValue(actual: String, target: String): String =
        text(R.string.mastering_check_value, actual, target)

    fun masteringBandLabel(band: MasteringBand): String = when (band) {
        MasteringBand.HighPass -> text(R.string.mastering_band_high_pass)
        MasteringBand.Body -> text(R.string.mastering_band_body)
        MasteringBand.MudCut -> text(R.string.mastering_band_mud_cut)
        MasteringBand.Warmth -> text(R.string.mastering_band_warmth)
        MasteringBand.Presence -> text(R.string.mastering_band_presence)
        MasteringBand.Air -> text(R.string.mastering_band_air)
    }

    fun masteringTargetLabel(target: MasteringTarget): String = when (target) {
        MasteringTarget.Streaming -> text(R.string.ui_audio_effects_preset_streaming)
        MasteringTarget.Club -> text(R.string.mastering_target_club)
        MasteringTarget.Podcast -> text(R.string.ui_audio_effects_preset_podcast)
        MasteringTarget.AppleDigital -> text(R.string.mastering_target_apple)
    }

    fun masteringTargetNote(target: MasteringTarget): String = when (target) {
        MasteringTarget.Streaming -> text(R.string.mastering_target_streaming_note)
        MasteringTarget.Club -> text(R.string.mastering_target_club_note)
        MasteringTarget.Podcast -> text(R.string.mastering_target_podcast_note)
        MasteringTarget.AppleDigital -> text(R.string.mastering_target_apple_note)
    }

    fun masteringShapeLabel(shape: MasteringModel.WaveformShape): String = when (shape) {
        MasteringModel.WaveformShape.Thick -> text(R.string.mastering_shape_thick)
        MasteringModel.WaveformShape.Dynamic -> text(R.string.mastering_shape_dynamic)
        MasteringModel.WaveformShape.Sparse -> text(R.string.mastering_shape_sparse)
    }

    fun masteringShapeNote(shape: MasteringModel.WaveformShape): String = when (shape) {
        MasteringModel.WaveformShape.Thick -> text(R.string.mastering_shape_thick_note)
        MasteringModel.WaveformShape.Dynamic -> text(R.string.mastering_shape_dynamic_note)
        MasteringModel.WaveformShape.Sparse -> text(R.string.mastering_shape_sparse_note)
    }

    fun masteringVerdictLabel(verdict: MasteringModel.Verdict): String = when (verdict) {
        MasteringModel.Verdict.Healthy -> text(R.string.mastering_verdict_healthy)
        MasteringModel.Verdict.Clipping -> text(R.string.mastering_verdict_clipping)
        MasteringModel.Verdict.TooQuiet -> text(R.string.mastering_verdict_too_quiet)
        MasteringModel.Verdict.OverCompressed -> text(R.string.mastering_verdict_over_compressed)
    }

    fun masteringVerdictNote(verdict: MasteringModel.Verdict): String = when (verdict) {
        MasteringModel.Verdict.Healthy -> text(R.string.mastering_verdict_healthy_note)
        MasteringModel.Verdict.Clipping -> text(R.string.mastering_verdict_clipping_note)
        MasteringModel.Verdict.TooQuiet -> text(R.string.mastering_verdict_too_quiet_note)
        MasteringModel.Verdict.OverCompressed -> text(R.string.mastering_verdict_over_compressed_note)
    }

    fun masteringTargetLufs(value: String): String = text(R.string.mastering_target_lufs_value, value)
    fun masteringTargetPeak(value: String): String = text(R.string.mastering_target_peak_value, value)

    fun stemLabel(kind: StemKind): String = when (kind) {
        StemKind.Vocals -> stemsVocals()
        StemKind.Drums -> stemsDrums()
        StemKind.Bass -> text(R.string.stems_bass)
        StemKind.Other -> text(R.string.stems_other)
    }
}
