# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LATIN_IME_JNI_SRC_FILES := \
    com_android_inputmethod_keyboard_ProximityInfo.cpp \
    com_android_inputmethod_latin_BinaryDictionary.cpp \
    com_android_inputmethod_latin_BinaryDictionaryUtils.cpp \
    com_android_inputmethod_latin_DicTraverseSession.cpp \
    jni_common.cpp

LATIN_IME_CORE_SRC_FILES := \
    $(addprefix dictionary/header/, \
        header_policy.cpp \
        header_read_write_utils.cpp) \
    dictionary/property/ngram_context.cpp \
    dictionary/structure/dictionary_structure_with_buffer_policy_factory.cpp \
    $(addprefix dictionary/structure/pt_common/, \
        bigram/bigram_list_read_write_utils.cpp \
        dynamic_pt_gc_event_listeners.cpp \
        dynamic_pt_reading_helper.cpp \
        dynamic_pt_reading_utils.cpp \
        dynamic_pt_updating_helper.cpp \
        dynamic_pt_writing_utils.cpp \
        patricia_trie_reading_utils.cpp \
        shortcut/shortcut_list_reading_utils.cpp) \
    $(addprefix dictionary/structure/v2/, \
        patricia_trie_policy.cpp \
        ver2_patricia_trie_node_reader.cpp \
        ver2_pt_node_array_reader.cpp) \
    $(addprefix dictionary/structure/v4/, \
        ver4_dict_buffers.cpp \
        ver4_dict_constants.cpp \
        ver4_patricia_trie_node_reader.cpp \
        ver4_patricia_trie_node_writer.cpp \
        ver4_patricia_trie_policy.cpp \
        ver4_patricia_trie_reading_utils.cpp \
        ver4_patricia_trie_writing_helper.cpp \
        ver4_pt_node_array_reader.cpp) \
    $(addprefix dictionary/structure/v4/content/, \
        dynamic_language_model_probability_utils.cpp \
        language_model_dict_content.cpp \
        language_model_dict_content_global_counters.cpp \
        shortcut_dict_content.cpp \
        sparse_table_dict_content.cpp \
        terminal_position_lookup_table.cpp) \
    $(addprefix dictionary/utils/, \
        buffer_with_extendable_buffer.cpp \
        byte_array_utils.cpp \
        dict_file_writing_utils.cpp \
        file_utils.cpp \
        forgetting_curve_utils.cpp \
        format_utils.cpp \
        mmapped_buffer.cpp \
        multi_bigram_map.cpp \
        probability_utils.cpp \
        sparse_table.cpp \
        trie_map.cpp ) \
    suggest/core/suggest.cpp \
    $(addprefix suggest/core/dicnode/, \
        dic_node.cpp \
        dic_node_utils.cpp \
        dic_nodes_cache.cpp) \
    $(addprefix suggest/core/dictionary/, \
        dictionary.cpp \
        dictionary_utils.cpp \
        digraph_utils.cpp \
        error_type_utils.cpp ) \
    $(addprefix suggest/core/layout/, \
        additional_proximity_chars.cpp \
        proximity_info.cpp \
        proximity_info_params.cpp \
        proximity_info_state.cpp \
        proximity_info_state_utils.cpp) \
    suggest/core/policy/weighting.cpp \
    suggest/core/session/dic_traverse_session.cpp \
    $(addprefix suggest/core/result/, \
        suggestion_results.cpp \
        suggestions_output_utils.cpp) \
    suggest/policyimpl/gesture/gesture_suggest_policy_factory.cpp \
    $(addprefix suggest/policyimpl/typing/, \
        scoring_params.cpp \
        typing_scoring.cpp \
        typing_suggest_policy.cpp \
        typing_traversal.cpp \
        typing_weighting.cpp) \
    $(addprefix utils/, \
        autocorrection_threshold_utils.cpp \
        char_utils.cpp \
        jni_data_utils.cpp \
        log_utils.cpp \
        time_keeper.cpp)

LATIN_IME_CORE_SRC_FILES_BACKWARD_V402 := \
    $(addprefix dictionary/structure/backward/v402/, \
        ver4_dict_buffers.cpp \
        ver4_dict_constants.cpp \
        ver4_patricia_trie_node_reader.cpp \
        ver4_patricia_trie_node_writer.cpp \
        ver4_patricia_trie_policy.cpp \
        ver4_patricia_trie_reading_utils.cpp \
        ver4_patricia_trie_writing_helper.cpp \
        ver4_pt_node_array_reader.cpp) \
    $(addprefix dictionary/structure/backward/v402/content/, \
        bigram_dict_content.cpp \
        probability_dict_content.cpp \
        shortcut_dict_content.cpp \
        sparse_table_dict_content.cpp \
        terminal_position_lookup_table.cpp) \
    $(addprefix dictionary/structure/backward/v402/bigram/, \
        ver4_bigram_list_policy.cpp)

LATIN_IME_CORE_SRC_FILES += $(LATIN_IME_CORE_SRC_FILES_BACKWARD_V402)

LATIN_IME_CORE_TEST_FILES := \
    defines_test.cpp \
    dictionary/header/header_read_write_utils_test.cpp \
    dictionary/structure/v4/content/language_model_dict_content_test.cpp \
    dictionary/structure/v4/content/language_model_dict_content_global_counters_test.cpp \
    dictionary/structure/v4/content/probability_entry_test.cpp \
    dictionary/structure/v4/content/terminal_position_lookup_table_test.cpp \
    dictionary/utils/bloom_filter_test.cpp \
    dictionary/utils/buffer_with_extendable_buffer_test.cpp \
    dictionary/utils/byte_array_utils_test.cpp \
    dictionary/utils/format_utils_test.cpp \
    dictionary/utils/probability_utils_test.cpp \
    dictionary/utils/sparse_table_test.cpp \
    dictionary/utils/trie_map_test.cpp \
    suggest/core/dicnode/dic_node_pool_test.cpp \
    suggest/core/layout/geometry_utils_test.cpp \
    suggest/core/layout/normal_distribution_2d_test.cpp \
    suggest/policyimpl/utils/damerau_levenshtein_edit_distance_policy_test.cpp \
    utils/autocorrection_threshold_utils_test.cpp \
    utils/char_utils_test.cpp \
    utils/int_array_view_test.cpp \
    utils/time_keeper_test.cpp
