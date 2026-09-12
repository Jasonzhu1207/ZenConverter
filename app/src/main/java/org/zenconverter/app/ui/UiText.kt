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

    val opusSampleRateHint: String
        get() = text(R.string.ui_opus_sample_rate_hint)

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

    fun volumeLabel(): String {
        return text(R.string.text_volume_label)
    }

    fun echoLabel(): String {
        return text(R.string.text_echo_label)
    }

    fun noiseReductionLabel(): String {
        return text(R.string.text_noise_reduction_label)
    }

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
            "Auto bitrate" -> text(R.string.text_option_value_auto_recommended)
            "Auto audio bitrate" -> text(R.string.text_option_value_auto_encoder_default)
            "Recommended audio bitrate" -> text(R.string.text_option_value_recommended_192_kbps)
            "High audio bitrate" -> text(R.string.text_option_value_high_256_kbps)
            "Compact audio bitrate" -> text(R.string.text_option_value_compact_128_kbps)
            "Voice audio bitrate" -> text(R.string.text_option_value_voice_96_kbps)
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
}
