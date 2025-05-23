/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.deviceinfo;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Vulkan info collector.
 *
 * This collector gathers a VkJSONInstance representing the Vulkan capabilities of the Android
 * device, and translates it into a DeviceInfoStore. The goal is to be as faithful to the original
 * VkJSON as possible, so that the DeviceInfo can later be turned back into VkJSON without loss,
 * while still allow complex queries against the DeviceInfo database.
 *
 * We inherit some design decisions from VkJSON, and there are a few places were translation isn't
 * perfect:
 *
 * - Most JSON implementations handle JSON Numbers as doubles (64-bit floating point), which can't
 *   faithfully transfer 64-bit integers. So Vulkan uint64_t and VkDeviceSize values are encoded as
 *   Strings containing the hexadecimal representation of the value (with "0x" prefix).
 *
 * - Vulkan enum values are represented as Numbers. This is most convenient for processing, though
 *   isn't very human-readable. Pretty-printing these as strings is left for other tools.
 *
 * - For implementation convenience, VkJSON represents VkBool32 values as JSON Numbers (0/1). This
 *   collector converts them to JSON Boolean values (false/true).
 *
 * - DeviceInfoStore doesn't allow arrays of non-uniform or non-primitive types. VkJSON stores
 *   format capabilities as an array of formats, where each format is an array containing a number
 *   (the format enum value) and an object (the format properties). Since DeviceInfoStore doesn't
 *   allow array-of-array, we instead store formats as an array of uniform structs, So instead of
 *       [[3, {
 *           "linearTilingFeatures": 0,
 *           "optimalTilingFeatures": 5121,
 *           "bufferFeatures": 0
 *       }]]
 *   the format with enum value "3" will be represented as
 *       {
 *           "id": 3,
 *           "linear_tiling_features": 0,
 *           "optimal_tiling_features": 5121,
 *           "buffer_features": 0
 *       }
 *
 * - Device layers are deprecated, but instance layers can still add device extensions. VkJSON
 *   doesn't yet include device extensions provided by layers, though. So VulkanDeviceInfo omits
 *   device layers altogether. Eventually VkJSON and VulkanDeviceInfo should report device layers
 *   and their extensions the same way instance layers and their extensions are reported.
 *
 * - VkJSON uses the original Vulkan field names, while VulkanDeviceInfo follows the DeviceInfo
 *   naming convention. So VkJSON fields named like "sparseProperties" will be converted to names
 *   like "sparse_properties".
 */
public final class VulkanDeviceInfo extends DeviceInfo {

    private static final String KEY_16BIT_STORAGE_FEATURES = "16bitStorageFeatures";
    private static final String KEY_ALPHA_TO_ONE = "alphaToOne";
    private static final String KEY_API_VERSION = "apiVersion";
    private static final String KEY_BUFFER_DEVICE_ADDRESS = "bufferDeviceAddress";
    private static final String KEY_BUFFER_DEVICE_ADDRESS_CAPTURE_REPLAY = "bufferDeviceAddressCaptureReplay";
    private static final String KEY_BUFFER_DEVICE_ADDRESS_MULTI_DEVICE = "bufferDeviceAddressMultiDevice";
    private static final String KEY_BUFFER_FEATURES = "bufferFeatures";
    private static final String KEY_BUFFER_IMAGE_GRANULARITY = "bufferImageGranularity";
    private static final String KEY_COMPATIBLE_HANDLE_TYPES = "compatibleHandleTypes";
    private static final String KEY_COMPUTE_FULL_SUBGROUPS = "computeFullSubgroups";
    private static final String KEY_CONFORMANCE_VERSION = "conformanceVersion";
    private static final String KEY_CORE12 = "core12";
    private static final String KEY_CORE13 = "core13";
    private static final String KEY_DENORM_BEHAVIOR_INDEPENDENCE = "denormBehaviorIndependence";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_DEPTH_BIAS_CLAMP = "depthBiasClamp";
    private static final String KEY_DEPTH_BOUNDS = "depthBounds";
    private static final String KEY_DEPTH_CLAMP = "depthClamp";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_DESCRIPTOR_BINDING_INLINE_UNIFORM_BLOCK_UPDATE_AFTER_BIND = "descriptorBindingInlineUniformBlockUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_PARTIALLY_BOUND = "descriptorBindingPartiallyBound";
    private static final String KEY_DESCRIPTOR_BINDING_SAMPLED_IMAGE_UPDATE_AFTER_BIND = "descriptorBindingSampledImageUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_STORAGE_BUFFER_UPDATE_AFTER_BIND = "descriptorBindingStorageBufferUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_STORAGE_IMAGE_UPDATE_AFTER_BIND = "descriptorBindingStorageImageUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_STORAGE_TEXEL_BUFFER_UPDATE_AFTER_BIND = "descriptorBindingStorageTexelBufferUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_UNIFORM_BUFFER_UPDATE_AFTER_BIND = "descriptorBindingUniformBufferUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_UNIFORM_TEXEL_BUFFER_UPDATE_AFTER_BIND = "descriptorBindingUniformTexelBufferUpdateAfterBind";
    private static final String KEY_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING = "descriptorBindingUpdateUnusedWhilePending";
    private static final String KEY_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT = "descriptorBindingVariableDescriptorCount";
    private static final String KEY_DESCRIPTOR_INDEXING = "descriptorIndexing";
    private static final String KEY_DEVICES = "devices";
    private static final String KEY_DEVICE_GROUPS = "deviceGroups";
    private static final String KEY_DEVICE_ID = "deviceID";
    private static final String KEY_DEVICE_LUID = "deviceLUID";
    private static final String KEY_DEVICE_LUID_VALID = "deviceLUIDValid";
    private static final String KEY_DEVICE_NAME = "deviceName";
    private static final String KEY_DEVICE_NODE_MASK = "deviceNodeMask";
    private static final String KEY_DEVICE_TYPE = "deviceType";
    private static final String KEY_DEVICE_UUID = "deviceUUID";
    private static final String KEY_DISCRETE_QUEUE_PRIORITIES = "discreteQueuePriorities";
    private static final String KEY_DRAW_INDIRECT_COUNT = "drawIndirectCount";
    private static final String KEY_DRAW_INDIRECT_FIRST_INSTANCE = "drawIndirectFirstInstance";
    private static final String KEY_DRIVER_ID = "driverID";
    private static final String KEY_DRIVER_INFO = "driverInfo";
    private static final String KEY_DRIVER_NAME = "driverName";
    private static final String KEY_DRIVER_PROPERTIES_KHR = "driverPropertiesKHR";
    private static final String KEY_DRIVER_UUID = "driverUUID";
    private static final String KEY_DRIVER_VERSION = "driverVersion";
    private static final String KEY_DUAL_SRC_BLEND = "dualSrcBlend";
    private static final String KEY_DYNAMIC_RENDERING = "dynamicRendering";
    private static final String KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES = "exportFromImportedHandleTypes";
    private static final String KEY_EXTENSIONS = "extensions";
    private static final String KEY_EXTENSION_NAME = "extensionName";
    private static final String KEY_EXTERNAL_FENCE_FEATURES = "externalFenceFeatures";
    private static final String KEY_EXTERNAL_FENCE_PROPERTIES = "externalFenceProperties";
    private static final String KEY_EXTERNAL_SEMAPHORE_FEATURES = "externalSemaphoreFeatures";
    private static final String KEY_EXTERNAL_SEMAPHORE_PROPERTIES = "externalSemaphoreProperties";
    private static final String KEY_FEATURES = "features";
    private static final String KEY_FILL_MODE_NON_SOLID = "fillModeNonSolid";
    private static final String KEY_FILTER_MINMAX_IMAGE_COMPONENT_MAPPING = "filterMinmaxImageComponentMapping";
    private static final String KEY_FILTER_MINMAX_SINGLE_COMPONENT_FORMATS = "filterMinmaxSingleComponentFormats";
    private static final String KEY_FLAGS = "flags";
    private static final String KEY_FORMATS = "formats";
    private static final String KEY_FRAGMENT_STORES_AND_ATOMICS = "fragmentStoresAndAtomics";
    private static final String KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS = "framebufferColorSampleCounts";
    private static final String KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS = "framebufferDepthSampleCounts";
    private static final String KEY_FRAMEBUFFER_INTEGER_COLOR_SAMPLE_COUNTS = "framebufferIntegerColorSampleCounts";
    private static final String KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS = "framebufferNoAttachmentsSampleCounts";
    private static final String KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS = "framebufferStencilSampleCounts";
    private static final String KEY_FULL_DRAW_INDEX_UINT32 = "fullDrawIndexUint32";
    private static final String KEY_GEOMETRY_SHADER = "geometryShader";
    private static final String KEY_HEAP_INDEX = "heapIndex";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_HOST_QUERY_RESET = "hostQueryReset";
    private static final String KEY_ID_PROPERTIES = "idProperties";
    private static final String KEY_IMAGELESS_FRAMEBUFFER = "imagelessFramebuffer";
    private static final String KEY_IMAGE_CUBE_ARRAY = "imageCubeArray";
    private static final String KEY_IMPLEMENTATION_VERSION = "implementationVersion";
    private static final String KEY_INDEPENDENT_BLEND = "independentBlend";
    private static final String KEY_INDEPENDENT_RESOLVE = "independentResolve";
    private static final String KEY_INDEPENDENT_RESOLVE_NONE = "independentResolveNone";
    private static final String KEY_INHERITED_QUERIES = "inheritedQueries";
    private static final String KEY_INLINE_UNIFORM_BLOCK = "inlineUniformBlock";
    private static final String KEY_INSTANCE_API_VERSION = "instanceApiVersion";
    private static final String KEY_INTEGER_DOT_PRODUCT_16BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProduct16BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_16BIT_SIGNED_ACCELERATED = "integerDotProduct16BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_16BIT_UNSIGNED_ACCELERATED = "integerDotProduct16BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_32BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProduct32BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_32BIT_SIGNED_ACCELERATED = "integerDotProduct32BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_32BIT_UNSIGNED_ACCELERATED = "integerDotProduct32BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProduct4x8BitPackedMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_SIGNED_ACCELERATED = "integerDotProduct4x8BitPackedSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_UNSIGNED_ACCELERATED = "integerDotProduct4x8BitPackedUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_64BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProduct64BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_64BIT_SIGNED_ACCELERATED = "integerDotProduct64BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_64BIT_UNSIGNED_ACCELERATED = "integerDotProduct64BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_8BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProduct8BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_8BIT_SIGNED_ACCELERATED = "integerDotProduct8BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_8BIT_UNSIGNED_ACCELERATED = "integerDotProduct8BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating16BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_SIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating16BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_UNSIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating16BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating32BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_SIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating32BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_UNSIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating32BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating4x8BitPackedMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_SIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating4x8BitPackedSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_UNSIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating4x8BitPackedUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating64BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating64BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_UNSIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating64BitUnsignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_MIXED_SIGNEDNESS_ACCELERATED = "integerDotProductAccumulatingSaturating8BitMixedSignednessAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_SIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating8BitSignedAccelerated";
    private static final String KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_UNSIGNED_ACCELERATED = "integerDotProductAccumulatingSaturating8BitUnsignedAccelerated";
    private static final String KEY_LARGE_POINTS = "largePoints";
    private static final String KEY_LAYERS = "layers";
    private static final String KEY_LAYER_NAME = "layerName";
    private static final String KEY_LIMITS = "limits";
    private static final String KEY_LINEAR_TILING_FEATURES = "linearTilingFeatures";
    private static final String KEY_LINE_WIDTH_GRANULARITY = "lineWidthGranularity";
    private static final String KEY_LINE_WIDTH_RANGE = "lineWidthRange";
    private static final String KEY_LOGIC_OP = "logicOp";
    private static final String KEY_MAINTENANCE4 = "maintenance4";
    private static final String KEY_MAINTENANCE_3_PROPERTIES = "maintenance3Properties";
    private static final String KEY_MAJOR = "major";
    private static final String KEY_MAX_BOUND_DESCRIPTOR_SETS = "maxBoundDescriptorSets";
    private static final String KEY_MAX_BUFFER_SIZE = "maxBufferSize";
    private static final String KEY_MAX_CLIP_DISTANCES = "maxClipDistances";
    private static final String KEY_MAX_COLOR_ATTACHMENTS = "maxColorAttachments";
    private static final String KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES = "maxCombinedClipAndCullDistances";
    private static final String KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE = "maxComputeSharedMemorySize";
    private static final String KEY_MAX_COMPUTE_WORKGROUP_SUBGROUPS = "maxComputeWorkgroupSubgroups";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_COUNT = "maxComputeWorkGroupCount";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS = "maxComputeWorkGroupInvocations";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_SIZE = "maxComputeWorkGroupSize";
    private static final String KEY_MAX_CULL_DISTANCES = "maxCullDistances";
    private static final String KEY_MAX_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCKS = "maxDescriptorSetInlineUniformBlocks";
    private static final String KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS = "maxDescriptorSetInputAttachments";
    private static final String KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES = "maxDescriptorSetSampledImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_SAMPLERS = "maxDescriptorSetSamplers";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS = "maxDescriptorSetStorageBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC = "maxDescriptorSetStorageBuffersDynamic";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES = "maxDescriptorSetStorageImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS = "maxDescriptorSetUniformBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC = "maxDescriptorSetUniformBuffersDynamic";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS = "maxDescriptorSetUpdateAfterBindInlineUniformBlocks";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS = "maxDescriptorSetUpdateAfterBindInputAttachments";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLED_IMAGES = "maxDescriptorSetUpdateAfterBindSampledImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLERS = "maxDescriptorSetUpdateAfterBindSamplers";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS = "maxDescriptorSetUpdateAfterBindStorageBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS_DYNAMIC = "maxDescriptorSetUpdateAfterBindStorageBuffersDynamic";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_IMAGES = "maxDescriptorSetUpdateAfterBindStorageImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS = "maxDescriptorSetUpdateAfterBindUniformBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS_DYNAMIC = "maxDescriptorSetUpdateAfterBindUniformBuffersDynamic";
    private static final String KEY_MAX_DRAW_INDEXED_INDEX_VALUE = "maxDrawIndexedIndexValue";
    private static final String KEY_MAX_DRAW_INDIRECT_COUNT = "maxDrawIndirectCount";
    private static final String KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES = "maxFragmentCombinedOutputResources";
    private static final String KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS = "maxFragmentDualSrcAttachments";
    private static final String KEY_MAX_FRAGMENT_INPUT_COMPONENTS = "maxFragmentInputComponents";
    private static final String KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS = "maxFragmentOutputAttachments";
    private static final String KEY_MAX_FRAMEBUFFER_HEIGHT = "maxFramebufferHeight";
    private static final String KEY_MAX_FRAMEBUFFER_LAYERS = "maxFramebufferLayers";
    private static final String KEY_MAX_FRAMEBUFFER_WIDTH = "maxFramebufferWidth";
    private static final String KEY_MAX_GEOMETRY_INPUT_COMPONENTS = "maxGeometryInputComponents";
    private static final String KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS = "maxGeometryOutputComponents";
    private static final String KEY_MAX_GEOMETRY_OUTPUT_VERTICES = "maxGeometryOutputVertices";
    private static final String KEY_MAX_GEOMETRY_SHADER_INVOCATIONS = "maxGeometryShaderInvocations";
    private static final String KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS = "maxGeometryTotalOutputComponents";
    private static final String KEY_MAX_IMAGE_ARRAY_LAYERS = "maxImageArrayLayers";
    private static final String KEY_MAX_IMAGE_DIMENSION_1D = "maxImageDimension1D";
    private static final String KEY_MAX_IMAGE_DIMENSION_2D = "maxImageDimension2D";
    private static final String KEY_MAX_IMAGE_DIMENSION_3D = "maxImageDimension3D";
    private static final String KEY_MAX_IMAGE_DIMENSION_CUBE = "maxImageDimensionCube";
    private static final String KEY_MAX_INLINE_UNIFORM_BLOCK_SIZE = "maxInlineUniformBlockSize";
    private static final String KEY_MAX_INLINE_UNIFORM_TOTAL_SIZE = "maxInlineUniformTotalSize";
    private static final String KEY_MAX_INTERPOLATION_OFFSET = "maxInterpolationOffset";
    private static final String KEY_MAX_MEMORY_ALLOCATION_COUNT = "maxMemoryAllocationCount";
    private static final String KEY_MAX_MEMORY_ALLOCATION_SIZE = "maxMemoryAllocationSize";
    private static final String KEY_MAX_MULTIVIEW_INSTANCE_INDEX = "maxMultiviewInstanceIndex";
    private static final String KEY_MAX_MULTIVIEW_VIEW_COUNT = "maxMultiviewViewCount";
    private static final String KEY_MAX_PER_SET_DESCRIPTORS = "maxPerSetDescriptors";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_INLINE_UNIFORM_BLOCKS = "maxPerStageDescriptorInlineUniformBlocks";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS = "maxPerStageDescriptorInputAttachments";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES = "maxPerStageDescriptorSampledImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS = "maxPerStageDescriptorSamplers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS = "maxPerStageDescriptorStorageBuffers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES = "maxPerStageDescriptorStorageImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS = "maxPerStageDescriptorUniformBuffers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS = "maxPerStageDescriptorUpdateAfterBindInlineUniformBlocks";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS = "maxPerStageDescriptorUpdateAfterBindInputAttachments";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLED_IMAGES = "maxPerStageDescriptorUpdateAfterBindSampledImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLERS = "maxPerStageDescriptorUpdateAfterBindSamplers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_BUFFERS = "maxPerStageDescriptorUpdateAfterBindStorageBuffers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_IMAGES = "maxPerStageDescriptorUpdateAfterBindStorageImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_UNIFORM_BUFFERS = "maxPerStageDescriptorUpdateAfterBindUniformBuffers";
    private static final String KEY_MAX_PER_STAGE_RESOURCES = "maxPerStageResources";
    private static final String KEY_MAX_PER_STAGE_UPDATE_AFTER_BIND_RESOURCES = "maxPerStageUpdateAfterBindResources";
    private static final String KEY_MAX_PUSH_CONSTANTS_SIZE = "maxPushConstantsSize";
    private static final String KEY_MAX_SAMPLER_ALLOCATION_COUNT = "maxSamplerAllocationCount";
    private static final String KEY_MAX_SAMPLER_ANISOTROPY = "maxSamplerAnisotropy";
    private static final String KEY_MAX_SAMPLER_LOD_BIAS = "maxSamplerLodBias";
    private static final String KEY_MAX_SAMPLE_MASK_WORDS = "maxSampleMaskWords";
    private static final String KEY_MAX_STORAGE_BUFFER_RANGE = "maxStorageBufferRange";
    private static final String KEY_MAX_SUBGROUP_SIZE = "maxSubgroupSize";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS = "maxTessellationControlPerPatchOutputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS = "maxTessellationControlPerVertexInputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS = "maxTessellationControlPerVertexOutputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS = "maxTessellationControlTotalOutputComponents";
    private static final String KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS = "maxTessellationEvaluationInputComponents";
    private static final String KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS = "maxTessellationEvaluationOutputComponents";
    private static final String KEY_MAX_TESSELLATION_GENERATION_LEVEL = "maxTessellationGenerationLevel";
    private static final String KEY_MAX_TESSELLATION_PATCH_SIZE = "maxTessellationPatchSize";
    private static final String KEY_MAX_TEXEL_BUFFER_ELEMENTS = "maxTexelBufferElements";
    private static final String KEY_MAX_TEXEL_GATHER_OFFSET = "maxTexelGatherOffset";
    private static final String KEY_MAX_TEXEL_OFFSET = "maxTexelOffset";
    private static final String KEY_MAX_TIMELINE_SEMAPHORE_VALUE_DIFFERENCE = "maxTimelineSemaphoreValueDifference";
    private static final String KEY_MAX_UNIFORM_BUFFER_RANGE = "maxUniformBufferRange";
    private static final String KEY_MAX_UPDATE_AFTER_BIND_DESCRIPTORS_IN_ALL_POOLS = "maxUpdateAfterBindDescriptorsInAllPools";
    private static final String KEY_MAX_VERTEX_INPUT_ATTRIBUTES = "maxVertexInputAttributes";
    private static final String KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET = "maxVertexInputAttributeOffset";
    private static final String KEY_MAX_VERTEX_INPUT_BINDINGS = "maxVertexInputBindings";
    private static final String KEY_MAX_VERTEX_INPUT_BINDING_STRIDE = "maxVertexInputBindingStride";
    private static final String KEY_MAX_VERTEX_OUTPUT_COMPONENTS = "maxVertexOutputComponents";
    private static final String KEY_MAX_VIEWPORTS = "maxViewports";
    private static final String KEY_MAX_VIEWPORT_DIMENSIONS = "maxViewportDimensions";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_MEMORY_HEAPS = "memoryHeaps";
    private static final String KEY_MEMORY_HEAP_COUNT = "memoryHeapCount";
    private static final String KEY_MEMORY_TYPES = "memoryTypes";
    private static final String KEY_MEMORY_TYPE_COUNT = "memoryTypeCount";
    private static final String KEY_MINOR = "minor";
    private static final String KEY_MIN_IMAGE_TRANSFER_GRANULARITY = "minImageTransferGranularity";
    private static final String KEY_MIN_INTERPOLATION_OFFSET = "minInterpolationOffset";
    private static final String KEY_MIN_MEMORY_MAP_ALIGNMENT = "minMemoryMapAlignment";
    private static final String KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT = "minStorageBufferOffsetAlignment";
    private static final String KEY_MIN_SUBGROUP_SIZE = "minSubgroupSize";
    private static final String KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT = "minTexelBufferOffsetAlignment";
    private static final String KEY_MIN_TEXEL_GATHER_OFFSET = "minTexelGatherOffset";
    private static final String KEY_MIN_TEXEL_OFFSET = "minTexelOffset";
    private static final String KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT = "minUniformBufferOffsetAlignment";
    private static final String KEY_MIPMAP_PRECISION_BITS = "mipmapPrecisionBits";
    private static final String KEY_MULTIVIEW = "multiview";
    private static final String KEY_MULTIVIEW_FEATURES = "multiviewFeatures";
    private static final String KEY_MULTIVIEW_GEOMETRY_SHADER = "multiviewGeometryShader";
    private static final String KEY_MULTIVIEW_PROPERTIES = "multiviewProperties";
    private static final String KEY_MULTIVIEW_TESSELLATION_SHADER = "multiviewTessellationShader";
    private static final String KEY_MULTI_DRAW_INDIRECT = "multiDrawIndirect";
    private static final String KEY_MULTI_VIEWPORT = "multiViewport";
    private static final String KEY_NON_COHERENT_ATOM_SIZE = "nonCoherentAtomSize";
    private static final String KEY_OCCLUSION_QUERY_PRECISE = "occlusionQueryPrecise";
    private static final String KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT = "optimalBufferCopyOffsetAlignment";
    private static final String KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT = "optimalBufferCopyRowPitchAlignment";
    private static final String KEY_OPTIMAL_TILING_FEATURES = "optimalTilingFeatures";
    private static final String KEY_PATCH = "patch";
    private static final String KEY_PIPELINE_CACHE_UUID = "pipelineCacheUUID";
    private static final String KEY_PIPELINE_CREATION_CACHE_CONTROL = "pipelineCreationCacheControl";
    private static final String KEY_PIPELINE_STATISTICS_QUERY = "pipelineStatisticsQuery";
    private static final String KEY_POINT_CLIPPING_BEHAVIOR = "pointClippingBehavior";
    private static final String KEY_POINT_CLIPPING_PROPERTIES = "pointClippingProperties";
    private static final String KEY_POINT_SIZE_GRANULARITY = "pointSizeGranularity";
    private static final String KEY_POINT_SIZE_RANGE = "pointSizeRange";
    private static final String KEY_PRIVATE_DATA = "privateData";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_PROPERTY_FLAGS = "propertyFlags";
    private static final String KEY_PROTECTED_MEMORY = "protectedMemory";
    private static final String KEY_PROTECTED_MEMORY_FEATURES = "protectedMemoryFeatures";
    private static final String KEY_QUAD_DIVERGENT_IMPLICIT_LOD = "quadDivergentImplicitLod";
    private static final String KEY_QUAD_OPERATIONS_IN_ALL_STAGES = "quadOperationsInAllStages";
    private static final String KEY_QUEUES = "queues";
    private static final String KEY_QUEUE_COUNT = "queueCount";
    private static final String KEY_QUEUE_FLAGS = "queueFlags";
    private static final String KEY_REQUIRED_SUBGROUP_SIZE_STAGES = "requiredSubgroupSizeStages";
    private static final String KEY_RESIDENCY_ALIGNED_MIP_SIZE = "residencyAlignedMipSize";
    private static final String KEY_RESIDENCY_NON_RESIDENT_STRICT = "residencyNonResidentStrict";
    private static final String KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE = "residencyStandard2DBlockShape";
    private static final String KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE = "residencyStandard2DMultisampleBlockShape";
    private static final String KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE = "residencyStandard3DBlockShape";
    private static final String KEY_ROBUST_BUFFER_ACCESS = "robustBufferAccess";
    private static final String KEY_ROBUST_BUFFER_ACCESS_UPDATE_AFTER_BIND = "robustBufferAccessUpdateAfterBind";
    private static final String KEY_ROBUST_IMAGE_ACCESS = "robustImageAccess";
    private static final String KEY_ROUNDING_MODE_INDEPENDENCE = "roundingModeIndependence";
    private static final String KEY_RUNTIME_DESCRIPTOR_ARRAY = "runtimeDescriptorArray";
    private static final String KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS = "sampledImageColorSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS = "sampledImageDepthSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS = "sampledImageIntegerSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS = "sampledImageStencilSampleCounts";
    private static final String KEY_SAMPLER_ANISOTROPY = "samplerAnisotropy";
    private static final String KEY_SAMPLER_FILTER_MINMAX = "samplerFilterMinmax";
    private static final String KEY_SAMPLER_MIRROR_CLAMP_TO_EDGE = "samplerMirrorClampToEdge";
    private static final String KEY_SAMPLER_YCBCR_CONVERSION = "samplerYcbcrConversion";
    private static final String KEY_SAMPLER_YCBCR_CONVERSION_FEATURES = "samplerYcbcrConversionFeatures";
    private static final String KEY_SAMPLE_RATE_SHADING = "sampleRateShading";
    private static final String KEY_SCALAR_BLOCK_LAYOUT = "scalarBlockLayout";
    private static final String KEY_SEPARATE_DEPTH_STENCIL_LAYOUTS = "separateDepthStencilLayouts";
    private static final String KEY_SHADER_BUFFER_INT64_ATOMICS = "shaderBufferInt64Atomics";
    private static final String KEY_SHADER_CLIP_DISTANCE = "shaderClipDistance";
    private static final String KEY_SHADER_CULL_DISTANCE = "shaderCullDistance";
    private static final String KEY_SHADER_DEMOTE_TO_HELPER_INVOCATION = "shaderDemoteToHelperInvocation";
    private static final String KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16 = "shaderDenormFlushToZeroFloat16";
    private static final String KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32 = "shaderDenormFlushToZeroFloat32";
    private static final String KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64 = "shaderDenormFlushToZeroFloat64";
    private static final String KEY_SHADER_DENORM_PRESERVE_FLOAT16 = "shaderDenormPreserveFloat16";
    private static final String KEY_SHADER_DENORM_PRESERVE_FLOAT32 = "shaderDenormPreserveFloat32";
    private static final String KEY_SHADER_DENORM_PRESERVE_FLOAT64 = "shaderDenormPreserveFloat64";
    private static final String KEY_SHADER_DRAW_PARAMETERS = "shaderDrawParameters";
    private static final String KEY_SHADER_DRAW_PARAMETER_FEATURES = "shaderDrawParameterFeatures";
    private static final String KEY_SHADER_FLOAT16 = "shaderFloat16";
    private static final String KEY_SHADER_FLOAT64 = "shaderFloat64";
    private static final String KEY_SHADER_IMAGE_GATHER_EXTENDED = "shaderImageGatherExtended";
    private static final String KEY_SHADER_INPUT_ATTACHMENT_ARRAY_DYNAMIC_INDEXING = "shaderInputAttachmentArrayDynamicIndexing";
    private static final String KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NONUNIFORM_INDEXING_NATIVE = "shaderInputAttachmentArrayNonUniformIndexingNative";
    private static final String KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NON_UNIFORM_INDEXING = "shaderInputAttachmentArrayNonUniformIndexing";
    private static final String KEY_SHADER_INT16 = "shaderInt16";
    private static final String KEY_SHADER_INT64 = "shaderInt64";
    private static final String KEY_SHADER_INT8 = "shaderInt8";
    private static final String KEY_SHADER_INTEGER_DOT_PRODUCT = "shaderIntegerDotProduct";
    private static final String KEY_SHADER_OUTPUT_LAYER = "shaderOutputLayer";
    private static final String KEY_SHADER_OUTPUT_VIEWPORT_INDEX = "shaderOutputViewportIndex";
    private static final String KEY_SHADER_RESOURCE_MIN_LOD = "shaderResourceMinLod";
    private static final String KEY_SHADER_RESOURCE_RESIDENCY = "shaderResourceResidency";
    private static final String KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16 = "shaderRoundingModeRTEFloat16";
    private static final String KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32 = "shaderRoundingModeRTEFloat32";
    private static final String KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64 = "shaderRoundingModeRTEFloat64";
    private static final String KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16 = "shaderRoundingModeRTZFloat16";
    private static final String KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32 = "shaderRoundingModeRTZFloat32";
    private static final String KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64 = "shaderRoundingModeRTZFloat64";
    private static final String KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING = "shaderSampledImageArrayDynamicIndexing";
    private static final String KEY_SHADER_SAMPLED_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE = "shaderSampledImageArrayNonUniformIndexingNative";
    private static final String KEY_SHADER_SAMPLED_IMAGE_ARRAY_NON_UNIFORM_INDEXING = "shaderSampledImageArrayNonUniformIndexing";
    private static final String KEY_SHADER_SHARED_INT64_ATOMICS = "shaderSharedInt64Atomics";
    private static final String KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16 = "shaderSignedZeroInfNanPreserveFloat16";
    private static final String KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32 = "shaderSignedZeroInfNanPreserveFloat32";
    private static final String KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64 = "shaderSignedZeroInfNanPreserveFloat64";
    private static final String KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderStorageBufferArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE = "shaderStorageBufferArrayNonUniformIndexingNative";
    private static final String KEY_SHADER_STORAGE_BUFFER_ARRAY_NON_UNIFORM_INDEXING = "shaderStorageBufferArrayNonUniformIndexing";
    private static final String KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING = "shaderStorageImageArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE = "shaderStorageImageArrayNonUniformIndexingNative";
    private static final String KEY_SHADER_STORAGE_IMAGE_ARRAY_NON_UNIFORM_INDEXING = "shaderStorageImageArrayNonUniformIndexing";
    private static final String KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS = "shaderStorageImageExtendedFormats";
    private static final String KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE = "shaderStorageImageMultisample";
    private static final String KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT = "shaderStorageImageReadWithoutFormat";
    private static final String KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT = "shaderStorageImageWriteWithoutFormat";
    private static final String KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderStorageTexelBufferArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING = "shaderStorageTexelBufferArrayNonUniformIndexing";
    private static final String KEY_SHADER_SUBGROUP_EXTENDED_TYPES = "shaderSubgroupExtendedTypes";
    private static final String KEY_SHADER_TERMINATE_INVOCATION = "shaderTerminateInvocation";
    private static final String KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE = "shaderTessellationAndGeometryPointSize";
    private static final String KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderUniformBufferArrayDynamicIndexing";
    private static final String KEY_SHADER_UNIFORM_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE = "shaderUniformBufferArrayNonUniformIndexingNative";
    private static final String KEY_SHADER_UNIFORM_BUFFER_ARRAY_NON_UNIFORM_INDEXING = "shaderUniformBufferArrayNonUniformIndexing";
    private static final String KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderUniformTexelBufferArrayDynamicIndexing";
    private static final String KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING = "shaderUniformTexelBufferArrayNonUniformIndexing";
    private static final String KEY_SHADER_ZERO_INITIALIZE_WORKGROUP_MEMORY = "shaderZeroInitializeWorkgroupMemory";
    private static final String KEY_SIZE = "size";
    private static final String KEY_SPARSE_ADDRESS_SPACE_SIZE = "sparseAddressSpaceSize";
    private static final String KEY_SPARSE_BINDING = "sparseBinding";
    private static final String KEY_SPARSE_PROPERTIES = "sparseProperties";
    private static final String KEY_SPARSE_RESIDENCY_16_SAMPLES = "sparseResidency16Samples";
    private static final String KEY_SPARSE_RESIDENCY_2_SAMPLES = "sparseResidency2Samples";
    private static final String KEY_SPARSE_RESIDENCY_4_SAMPLES = "sparseResidency4Samples";
    private static final String KEY_SPARSE_RESIDENCY_8_SAMPLES = "sparseResidency8Samples";
    private static final String KEY_SPARSE_RESIDENCY_ALIASED = "sparseResidencyAliased";
    private static final String KEY_SPARSE_RESIDENCY_BUFFER = "sparseResidencyBuffer";
    private static final String KEY_SPARSE_RESIDENCY_IMAGE_2D = "sparseResidencyImage2D";
    private static final String KEY_SPARSE_RESIDENCY_IMAGE_3D = "sparseResidencyImage3D";
    private static final String KEY_SPEC_VERSION = "specVersion";
    private static final String KEY_STANDARD_SAMPLE_LOCATIONS = "standardSampleLocations";
    private static final String KEY_STORAGE_BUFFER_16BIT_ACCESS = "storageBuffer16BitAccess";
    private static final String KEY_STORAGE_BUFFER_8BIT_ACCESS = "storageBuffer8BitAccess";
    private static final String KEY_STORAGE_IMAGE_SAMPLE_COUNTS = "storageImageSampleCounts";
    private static final String KEY_STORAGE_INPUT_OUTPUT_16 = "storageInputOutput16";
    private static final String KEY_STORAGE_PUSH_CONSTANT8 = "storagePushConstant8";
    private static final String KEY_STORAGE_PUSH_CONSTANT_16 = "storagePushConstant16";
    private static final String KEY_STORAGE_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES = "storageTexelBufferOffsetAlignmentBytes";
    private static final String KEY_STORAGE_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT = "storageTexelBufferOffsetSingleTexelAlignment";
    private static final String KEY_STRICT_LINES = "strictLines";
    private static final String KEY_SUBGROUP_BROADCAST_DYNAMIC_ID = "subgroupBroadcastDynamicId";
    private static final String KEY_SUBGROUP_PROPERTIES = "subgroupProperties";
    private static final String KEY_SUBGROUP_SIZE = "subgroupSize";
    private static final String KEY_SUBGROUP_SIZE_CONTROL = "subgroupSizeControl";
    private static final String KEY_SUBMINOR = "subminor";
    private static final String KEY_SUBSET_ALLOCATION = "subsetAllocation";
    private static final String KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS = "subPixelInterpolationOffsetBits";
    private static final String KEY_SUB_PIXEL_PRECISION_BITS = "subPixelPrecisionBits";
    private static final String KEY_SUB_TEXEL_PRECISION_BITS = "subTexelPrecisionBits";
    private static final String KEY_SUPPORTED_DEPTH_RESOLVE_MODES = "supportedDepthResolveModes";
    private static final String KEY_SUPPORTED_OPERATIONS = "supportedOperations";
    private static final String KEY_SUPPORTED_STAGES = "supportedStages";
    private static final String KEY_SUPPORTED_STENCIL_RESOLVE_MODES = "supportedStencilResolveModes";
    private static final String KEY_SYNCHRONIZATION2 = "synchronization2";
    private static final String KEY_TESSELLATION_SHADER = "tessellationShader";
    private static final String KEY_TEXTURE_COMPRESSION_ASTC_HDR = "textureCompressionASTC_HDR";
    private static final String KEY_TEXTURE_COMPRESSION_ASTC_LDR = "textureCompressionASTC_LDR";
    private static final String KEY_TEXTURE_COMPRESSION_BC = "textureCompressionBC";
    private static final String KEY_TEXTURE_COMPRESSION_ETC2 = "textureCompressionETC2";
    private static final String KEY_TIMELINE_SEMAPHORE = "timelineSemaphore";
    private static final String KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS = "timestampComputeAndGraphics";
    private static final String KEY_TIMESTAMP_PERIOD = "timestampPeriod";
    private static final String KEY_TIMESTAMP_VALID_BITS = "timestampValidBits";
    private static final String KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS = "uniformAndStorageBuffer16BitAccess";
    private static final String KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS = "uniformAndStorageBuffer8BitAccess";
    private static final String KEY_UNIFORM_BUFFER_STANDARD_LAYOUT = "uniformBufferStandardLayout";
    private static final String KEY_UNIFORM_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES = "uniformTexelBufferOffsetAlignmentBytes";
    private static final String KEY_UNIFORM_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT = "uniformTexelBufferOffsetSingleTexelAlignment";
    private static final String KEY_VARIABLE_MULTISAMPLE_RATE = "variableMultisampleRate";
    private static final String KEY_VARIABLE_POINTERS = "variablePointers";
    private static final String KEY_VARIABLE_POINTERS_STORAGE_BUFFER = "variablePointersStorageBuffer";
    private static final String KEY_VARIABLE_POINTER_FEATURES = "variablePointerFeatures";
    private static final String KEY_VARIABLE_POINTER_FEATURES_KHR = "variablePointerFeaturesKHR";
    private static final String KEY_VENDOR_ID = "vendorID";
    private static final String KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS = "vertexPipelineStoresAndAtomics";
    private static final String KEY_VIEWPORT_BOUNDS_RANGE = "viewportBoundsRange";
    private static final String KEY_VIEWPORT_SUB_PIXEL_BITS = "viewportSubPixelBits";
    private static final String KEY_VK_KHR_DRIVER_PROPERTIES = "VK_KHR_driver_properties";
    private static final String KEY_VK_KHR_VARIABLE_POINTERS = "VK_KHR_variable_pointers";
    private static final String KEY_VULKAN_12_FEATURES = "vulkan_12_features";
    private static final String KEY_VULKAN_12_PROPERTIES = "vulkan_12_properties";
    private static final String KEY_VULKAN_13_FEATURES = "vulkan_13_features";
    private static final String KEY_VULKAN_13_PROPERTIES = "vulkan_13_properties";
    private static final String KEY_VULKAN_MEMORY_MODEL = "vulkanMemoryModel";
    private static final String KEY_VULKAN_MEMORY_MODEL_AVAILABILITY_VISIBILITY_CHAINS = "vulkanMemoryModelAvailabilityVisibilityChains";
    private static final String KEY_VULKAN_MEMORY_MODEL_DEVICE_SCOPE = "vulkanMemoryModelDeviceScope";
    private static final String KEY_WIDE_LINES = "wideLines";
    private static final String KEY_WIDTH = "width";

    private static final int VK_API_VERSION_1_1 = 4198400;
    private static final int VK_API_VERSION_1_2 = 4202496;
    private static final int VK_API_VERSION_1_3 = 4206592;
    private static final int ENUM_VK_KHR_VARIABLE_POINTERS = 0;
    private static final int ENUM_VK_KHR_DRIVER_PROPERTIES = 1;

    private static HashMap<String, Integer> extensionNameToEnum;

    static {
        System.loadLibrary("ctsdeviceinfo");
        extensionNameToEnum = new HashMap<>();
        extensionNameToEnum.put(KEY_VK_KHR_DRIVER_PROPERTIES, ENUM_VK_KHR_DRIVER_PROPERTIES);
        extensionNameToEnum.put(KEY_VK_KHR_VARIABLE_POINTERS, ENUM_VK_KHR_VARIABLE_POINTERS);
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        try {
            JSONObject instance = new JSONObject(nativeGetVkJSON());
            emitDeviceGroups(store, instance);
            emitLayers(store, instance);
            emitExtensions(store, instance);
            emitDevices(store, instance);

            // Access to Instance API version was only added alongside 1.2 support in instance
            if (instance.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                emitInstanceApiVersion(store, instance);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void emitDeviceGroups(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray deviceGroups = parent.getJSONArray(KEY_DEVICE_GROUPS);
        store.startArray(convertName(KEY_DEVICE_GROUPS));
        for (int deviceGroupIdx = 0; deviceGroupIdx < deviceGroups.length(); deviceGroupIdx++) {
            JSONObject deviceGroup = deviceGroups.getJSONObject(deviceGroupIdx);
            store.startGroup();
            {
                emitLongArray(store, deviceGroup, KEY_DEVICES);
                emitBoolean(store, deviceGroup, KEY_SUBSET_ALLOCATION);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitDevices(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray devices = parent.getJSONArray(KEY_DEVICES);
        store.startArray(convertName(KEY_DEVICES));
        for (int deviceIdx = 0; deviceIdx < devices.length(); deviceIdx++) {
            JSONObject device = devices.getJSONObject(deviceIdx);
            store.startGroup();
            {
                JSONObject properties = device.getJSONObject(KEY_PROPERTIES);
                store.startGroup(convertName(KEY_PROPERTIES));
                {
                    emitLong(store, properties, KEY_API_VERSION);
                    emitLong(store, properties, KEY_DRIVER_VERSION);
                    emitLong(store, properties, KEY_VENDOR_ID);
                    emitLong(store, properties, KEY_DEVICE_ID);
                    emitLong(store, properties, KEY_DEVICE_TYPE);
                    emitString(store, properties, KEY_DEVICE_NAME);
                    emitLongArray(store, properties, KEY_PIPELINE_CACHE_UUID);

                    JSONObject limits = properties.getJSONObject(KEY_LIMITS);
                    store.startGroup(convertName(KEY_LIMITS));
                    {
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_1D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_2D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_3D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_CUBE);
                        emitLong(store, limits, KEY_MAX_IMAGE_ARRAY_LAYERS);
                        emitLong(store, limits, KEY_MAX_TEXEL_BUFFER_ELEMENTS);
                        emitLong(store, limits, KEY_MAX_UNIFORM_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_STORAGE_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_PUSH_CONSTANTS_SIZE);
                        emitLong(store, limits, KEY_MAX_MEMORY_ALLOCATION_COUNT);
                        emitLong(store, limits, KEY_MAX_SAMPLER_ALLOCATION_COUNT);
                        emitString(store, limits, KEY_BUFFER_IMAGE_GRANULARITY);
                        emitString(store, limits, KEY_SPARSE_ADDRESS_SPACE_SIZE);
                        emitLong(store, limits, KEY_MAX_BOUND_DESCRIPTOR_SETS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_RESOURCES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTES);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDINGS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDING_STRIDE);
                        emitLong(store, limits, KEY_MAX_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_GENERATION_LEVEL);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_PATCH_SIZE);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_SHADER_INVOCATIONS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_VERTICES);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES);
                        emitLong(store, limits, KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_COUNT);
                        emitLong(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_SIZE);
                        emitLong(store, limits, KEY_SUB_PIXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_SUB_TEXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_MIPMAP_PRECISION_BITS);
                        emitLong(store, limits, KEY_MAX_DRAW_INDEXED_INDEX_VALUE);
                        emitLong(store, limits, KEY_MAX_DRAW_INDIRECT_COUNT);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_LOD_BIAS);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_ANISOTROPY);
                        emitLong(store, limits, KEY_MAX_VIEWPORTS);
                        emitLongArray(store, limits, KEY_MAX_VIEWPORT_DIMENSIONS);
                        emitDoubleArray(store, limits, KEY_VIEWPORT_BOUNDS_RANGE);
                        emitLong(store, limits, KEY_VIEWPORT_SUB_PIXEL_BITS);
                        emitString(store, limits, KEY_MIN_MEMORY_MAP_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT);
                        emitLong(store, limits, KEY_MIN_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MIN_TEXEL_GATHER_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_GATHER_OFFSET);
                        emitDouble(store, limits, KEY_MIN_INTERPOLATION_OFFSET);
                        emitDouble(store, limits, KEY_MAX_INTERPOLATION_OFFSET);
                        emitLong(store, limits, KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_WIDTH);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_HEIGHT);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_LAYERS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_COLOR_ATTACHMENTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_STORAGE_IMAGE_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_SAMPLE_MASK_WORDS);
                        emitBoolean(store, limits, KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS);
                        emitDouble(store, limits, KEY_TIMESTAMP_PERIOD);
                        emitLong(store, limits, KEY_MAX_CLIP_DISTANCES);
                        emitLong(store, limits, KEY_MAX_CULL_DISTANCES);
                        emitLong(store, limits, KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES);
                        emitLong(store, limits, KEY_DISCRETE_QUEUE_PRIORITIES);
                        emitDoubleArray(store, limits, KEY_POINT_SIZE_RANGE);
                        emitDoubleArray(store, limits, KEY_LINE_WIDTH_RANGE);
                        emitDouble(store, limits, KEY_POINT_SIZE_GRANULARITY);
                        emitDouble(store, limits, KEY_LINE_WIDTH_GRANULARITY);
                        emitBoolean(store, limits, KEY_STRICT_LINES);
                        emitBoolean(store, limits, KEY_STANDARD_SAMPLE_LOCATIONS);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT);
                        emitString(store, limits, KEY_NON_COHERENT_ATOM_SIZE);
                    }
                    store.endGroup();

                    JSONObject sparse = properties.getJSONObject(KEY_SPARSE_PROPERTIES);
                    store.startGroup(convertName(KEY_SPARSE_PROPERTIES));
                    {
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_ALIGNED_MIP_SIZE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_NON_RESIDENT_STRICT);
                    }
                    store.endGroup();

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                        JSONObject core12 = device.getJSONObject(KEY_CORE12);
                        JSONObject vulkan12Properties = core12.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(convertName(KEY_VULKAN_12_PROPERTIES));
                        {
                            emitLong(store, vulkan12Properties, KEY_DRIVER_ID);
                            emitString(store, vulkan12Properties, KEY_DRIVER_NAME);
                            emitString(store, vulkan12Properties, KEY_DRIVER_INFO);

                            JSONObject conformanceVersion = vulkan12Properties.getJSONObject(KEY_CONFORMANCE_VERSION);
                            store.startGroup(convertName(KEY_CONFORMANCE_VERSION));
                            {
                                emitLong(store, conformanceVersion, KEY_MAJOR);
                                emitLong(store, conformanceVersion, KEY_MINOR);
                                emitLong(store, conformanceVersion, KEY_SUBMINOR);
                                emitLong(store, conformanceVersion, KEY_PATCH);
                            }
                            store.endGroup();

                            emitLong(store, vulkan12Properties, KEY_DENORM_BEHAVIOR_INDEPENDENCE);
                            emitLong(store, vulkan12Properties, KEY_ROUNDING_MODE_INDEPENDENCE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64);
                            emitLong(store, vulkan12Properties, KEY_MAX_UPDATE_AFTER_BIND_DESCRIPTORS_IN_ALL_POOLS);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_UNIFORM_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SAMPLED_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_STORAGE_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_STORAGE_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_ROBUST_BUFFER_ACCESS_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Properties, KEY_QUAD_DIVERGENT_IMPLICIT_LOD);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_UNIFORM_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLED_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_UPDATE_AFTER_BIND_RESOURCES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS_DYNAMIC);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS_DYNAMIC);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLED_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS);
                            emitLong(store, vulkan12Properties, KEY_SUPPORTED_DEPTH_RESOLVE_MODES);
                            emitLong(store, vulkan12Properties, KEY_SUPPORTED_STENCIL_RESOLVE_MODES);
                            emitBoolean(store, vulkan12Properties, KEY_INDEPENDENT_RESOLVE_NONE);
                            emitBoolean(store, vulkan12Properties, KEY_INDEPENDENT_RESOLVE);
                            emitBoolean(store, vulkan12Properties, KEY_FILTER_MINMAX_SINGLE_COMPONENT_FORMATS);
                            emitBoolean(store, vulkan12Properties, KEY_FILTER_MINMAX_IMAGE_COMPONENT_MAPPING);
                            emitString(store, vulkan12Properties, KEY_MAX_TIMELINE_SEMAPHORE_VALUE_DIFFERENCE);
                            emitLong(store, vulkan12Properties, KEY_FRAMEBUFFER_INTEGER_COLOR_SAMPLE_COUNTS);
                        }
                        store.endGroup();
                    }

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_3) {
                        JSONObject core13 = device.getJSONObject(KEY_CORE13);
                        JSONObject vulkan13Properties = core13.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(convertName(KEY_VULKAN_13_PROPERTIES));
                        {
                            emitLong(store, vulkan13Properties, KEY_MIN_SUBGROUP_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_SUBGROUP_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_COMPUTE_WORKGROUP_SUBGROUPS);
                            emitLong(store, vulkan13Properties, KEY_REQUIRED_SUBGROUP_SIZE_STAGES);
                            emitLong(store, vulkan13Properties, KEY_MAX_INLINE_UNIFORM_BLOCK_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_INLINE_UNIFORM_TOTAL_SIZE);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitString(store, vulkan13Properties, KEY_STORAGE_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES);
                            emitBoolean(store, vulkan13Properties, KEY_STORAGE_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT);
                            emitString(store, vulkan13Properties, KEY_UNIFORM_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES);
                            emitBoolean(store, vulkan13Properties, KEY_UNIFORM_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT);
                            emitString(store, vulkan13Properties, KEY_MAX_BUFFER_SIZE);
                        }
                        store.endGroup();
                    }
                }
                store.endGroup();

                JSONObject features = device.getJSONObject(KEY_FEATURES);
                store.startGroup(convertName(KEY_FEATURES));
                {
                    emitBoolean(store, features, KEY_ROBUST_BUFFER_ACCESS);
                    emitBoolean(store, features, KEY_FULL_DRAW_INDEX_UINT32);
                    emitBoolean(store, features, KEY_IMAGE_CUBE_ARRAY);
                    emitBoolean(store, features, KEY_INDEPENDENT_BLEND);
                    emitBoolean(store, features, KEY_GEOMETRY_SHADER);
                    emitBoolean(store, features, KEY_TESSELLATION_SHADER);
                    emitBoolean(store, features, KEY_SAMPLE_RATE_SHADING);
                    emitBoolean(store, features, KEY_DUAL_SRC_BLEND);
                    emitBoolean(store, features, KEY_LOGIC_OP);
                    emitBoolean(store, features, KEY_MULTI_DRAW_INDIRECT);
                    emitBoolean(store, features, KEY_DRAW_INDIRECT_FIRST_INSTANCE);
                    emitBoolean(store, features, KEY_DEPTH_CLAMP);
                    emitBoolean(store, features, KEY_DEPTH_BIAS_CLAMP);
                    emitBoolean(store, features, KEY_FILL_MODE_NON_SOLID);
                    emitBoolean(store, features, KEY_DEPTH_BOUNDS);
                    emitBoolean(store, features, KEY_WIDE_LINES);
                    emitBoolean(store, features, KEY_LARGE_POINTS);
                    emitBoolean(store, features, KEY_ALPHA_TO_ONE);
                    emitBoolean(store, features, KEY_MULTI_VIEWPORT);
                    emitBoolean(store, features, KEY_SAMPLER_ANISOTROPY);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ETC2);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ASTC_LDR);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_BC);
                    emitBoolean(store, features, KEY_OCCLUSION_QUERY_PRECISE);
                    emitBoolean(store, features, KEY_PIPELINE_STATISTICS_QUERY);
                    emitBoolean(store, features, KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_FRAGMENT_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE);
                    emitBoolean(store, features, KEY_SHADER_IMAGE_GATHER_EXTENDED);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_CLIP_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_CULL_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_FLOAT64);
                    emitBoolean(store, features, KEY_SHADER_INT64);
                    emitBoolean(store, features, KEY_SHADER_INT16);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_RESIDENCY);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_MIN_LOD);
                    emitBoolean(store, features, KEY_SPARSE_BINDING);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_BUFFER);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_2D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_3D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_2_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_4_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_8_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_16_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_ALIASED);
                    emitBoolean(store, features, KEY_VARIABLE_MULTISAMPLE_RATE);
                    emitBoolean(store, features, KEY_INHERITED_QUERIES);

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                        JSONObject core12 = device.getJSONObject(KEY_CORE12);
                        JSONObject vulkan12Features = core12.getJSONObject(KEY_FEATURES);
                        store.startGroup(convertName(KEY_VULKAN_12_FEATURES));
                        {
                            emitBoolean(store, vulkan12Features, KEY_SAMPLER_MIRROR_CLAMP_TO_EDGE);
                            emitBoolean(store, vulkan12Features, KEY_DRAW_INDIRECT_COUNT);
                            emitBoolean(store, vulkan12Features, KEY_STORAGE_BUFFER_8BIT_ACCESS);
                            emitBoolean(store, vulkan12Features, KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS);
                            emitBoolean(store, vulkan12Features, KEY_STORAGE_PUSH_CONSTANT8);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_BUFFER_INT64_ATOMICS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SHARED_INT64_ATOMICS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_FLOAT16);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INT8);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SAMPLED_IMAGE_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_IMAGE_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UNIFORM_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_SAMPLED_IMAGE_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_IMAGE_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UNIFORM_TEXEL_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_TEXEL_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_PARTIALLY_BOUND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT);
                            emitBoolean(store, vulkan12Features, KEY_RUNTIME_DESCRIPTOR_ARRAY);
                            emitBoolean(store, vulkan12Features, KEY_SAMPLER_FILTER_MINMAX);
                            emitBoolean(store, vulkan12Features, KEY_SCALAR_BLOCK_LAYOUT);
                            emitBoolean(store, vulkan12Features, KEY_IMAGELESS_FRAMEBUFFER);
                            emitBoolean(store, vulkan12Features, KEY_UNIFORM_BUFFER_STANDARD_LAYOUT);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SUBGROUP_EXTENDED_TYPES);
                            emitBoolean(store, vulkan12Features, KEY_SEPARATE_DEPTH_STENCIL_LAYOUTS);
                            emitBoolean(store, vulkan12Features, KEY_HOST_QUERY_RESET);
                            emitBoolean(store, vulkan12Features, KEY_TIMELINE_SEMAPHORE);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS_CAPTURE_REPLAY);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS_MULTI_DEVICE);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL_DEVICE_SCOPE);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL_AVAILABILITY_VISIBILITY_CHAINS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_OUTPUT_VIEWPORT_INDEX);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_OUTPUT_LAYER);
                            // subgroupBroadcastDynamicId was erroneously left out of vkjson reporting in Android T
                            //   and later added in U, so we need to explicitly check if the feature is reported
                            if (vulkan12Features.has(KEY_SUBGROUP_BROADCAST_DYNAMIC_ID)) {
                                emitBoolean(store, vulkan12Features, KEY_SUBGROUP_BROADCAST_DYNAMIC_ID);
                            }
                        }
                        store.endGroup();
                    }

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_3) {
                        JSONObject core13 = device.getJSONObject(KEY_CORE13);
                        JSONObject vulkan13Features = core13.getJSONObject(KEY_FEATURES);
                        store.startGroup(convertName(KEY_VULKAN_13_FEATURES));
                        {
                            emitBoolean(store, vulkan13Features, KEY_ROBUST_IMAGE_ACCESS);
                            emitBoolean(store, vulkan13Features, KEY_INLINE_UNIFORM_BLOCK);
                            emitBoolean(store, vulkan13Features, KEY_DESCRIPTOR_BINDING_INLINE_UNIFORM_BLOCK_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan13Features, KEY_PIPELINE_CREATION_CACHE_CONTROL);
                            emitBoolean(store, vulkan13Features, KEY_PRIVATE_DATA);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_DEMOTE_TO_HELPER_INVOCATION);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_TERMINATE_INVOCATION);
                            emitBoolean(store, vulkan13Features, KEY_SUBGROUP_SIZE_CONTROL);
                            emitBoolean(store, vulkan13Features, KEY_COMPUTE_FULL_SUBGROUPS);
                            emitBoolean(store, vulkan13Features, KEY_SYNCHRONIZATION2);
                            emitBoolean(store, vulkan13Features, KEY_TEXTURE_COMPRESSION_ASTC_HDR);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_ZERO_INITIALIZE_WORKGROUP_MEMORY);
                            emitBoolean(store, vulkan13Features, KEY_DYNAMIC_RENDERING);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_INTEGER_DOT_PRODUCT);
                            emitBoolean(store, vulkan13Features, KEY_MAINTENANCE4);
                        }
                        store.endGroup();
                    }
                }
                store.endGroup();

                JSONObject memory = device.getJSONObject(KEY_MEMORY);
                store.startGroup(convertName(KEY_MEMORY));
                {
                    emitLong(store, memory, KEY_MEMORY_TYPE_COUNT);
                    JSONArray memoryTypes = memory.getJSONArray(KEY_MEMORY_TYPES);
                    store.startArray(convertName(KEY_MEMORY_TYPES));
                    for (int memoryTypeIdx = 0; memoryTypeIdx < memoryTypes.length();
                            memoryTypeIdx++) {
                        JSONObject memoryType = memoryTypes.getJSONObject(memoryTypeIdx);
                        store.startGroup();
                        {
                            emitLong(store, memoryType, KEY_PROPERTY_FLAGS);
                            emitLong(store, memoryType, KEY_HEAP_INDEX);
                        }
                        store.endGroup();
                    }
                    store.endArray();

                    emitLong(store, memory, KEY_MEMORY_HEAP_COUNT);
                    JSONArray memoryHeaps = memory.getJSONArray(KEY_MEMORY_HEAPS);
                    store.startArray(convertName(KEY_MEMORY_HEAPS));
                    for (int memoryHeapIdx = 0; memoryHeapIdx < memoryHeaps.length();
                            memoryHeapIdx++) {
                        JSONObject memoryHeap = memoryHeaps.getJSONObject(memoryHeapIdx);
                        store.startGroup();
                        {
                            emitString(store, memoryHeap, KEY_SIZE);
                            emitLong(store, memoryHeap, KEY_FLAGS);
                        }
                        store.endGroup();
                    }
                    store.endArray();
                }
                store.endGroup();

                JSONArray queues = device.getJSONArray(KEY_QUEUES);
                store.startArray(convertName(KEY_QUEUES));
                for (int queueIdx = 0; queueIdx < queues.length(); queueIdx++) {
                    JSONObject queue = queues.getJSONObject(queueIdx);
                    store.startGroup();
                    {
                        emitLong(store, queue, KEY_QUEUE_FLAGS);
                        emitLong(store, queue, KEY_QUEUE_COUNT);
                        emitLong(store, queue, KEY_TIMESTAMP_VALID_BITS);
                        JSONObject extent = queue.getJSONObject(KEY_MIN_IMAGE_TRANSFER_GRANULARITY);
                        store.startGroup(convertName(KEY_MIN_IMAGE_TRANSFER_GRANULARITY));
                        {
                            emitLong(store, extent, KEY_WIDTH);
                            emitLong(store, extent, KEY_HEIGHT);
                            emitLong(store, extent, KEY_DEPTH);
                        }
                        store.endGroup();
                    }
                    store.endGroup();
                }
                store.endArray();

                // Skip layers for now. VkJSON doesn't yet include device layer extensions, so
                // this is entirely redundant with the instance extension information.
                // emitLayers(store, device);
                store.startArray(convertName(KEY_LAYERS));
                store.endArray();

                emitExtensions(store, device);

                JSONArray formats = device.getJSONArray(KEY_FORMATS);
                // Note: Earlier code used field named 'formats' with different data structure.
                // In order to have the mix of old and new data, we cannot reuse that name.
                store.startArray("supported_formats");
                for (int formatIdx = 0; formatIdx < formats.length(); formatIdx++) {
                    JSONArray formatPair = formats.getJSONArray(formatIdx);
                    JSONObject formatProperties = formatPair.getJSONObject(1);
                    store.startGroup();
                    {
                        store.addResult("format", (long)formatPair.getInt(0));
                        emitLong(store, formatProperties, KEY_LINEAR_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_OPTIMAL_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_BUFFER_FEATURES);
                    }
                    store.endGroup();
                }
                store.endArray();

                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_1) {
                    JSONObject subgroupProperties = device.getJSONObject(KEY_SUBGROUP_PROPERTIES);
                    store.startGroup(convertName(KEY_SUBGROUP_PROPERTIES));
                    {
                        emitLong(store, subgroupProperties, KEY_SUBGROUP_SIZE);
                        emitLong(store, subgroupProperties, KEY_SUPPORTED_STAGES);
                        emitLong(store, subgroupProperties, KEY_SUPPORTED_OPERATIONS);
                        emitBoolean(store, subgroupProperties, KEY_QUAD_OPERATIONS_IN_ALL_STAGES);
                    }
                    store.endGroup();

                    JSONObject pointClippingProperties = device.getJSONObject(KEY_POINT_CLIPPING_PROPERTIES);
                    store.startGroup(convertName(KEY_POINT_CLIPPING_PROPERTIES));
                    {
                        emitLong(store, pointClippingProperties, KEY_POINT_CLIPPING_BEHAVIOR);
                    }
                    store.endGroup();

                    JSONObject multiviewProperties = device.getJSONObject(KEY_MULTIVIEW_PROPERTIES);
                    store.startGroup(convertName(KEY_MULTIVIEW_PROPERTIES));
                    {
                        emitLong(store, multiviewProperties, KEY_MAX_MULTIVIEW_VIEW_COUNT);
                        emitLong(store, multiviewProperties, KEY_MAX_MULTIVIEW_INSTANCE_INDEX);
                    }
                    store.endGroup();

                    JSONObject idProperties = device.getJSONObject(KEY_ID_PROPERTIES);
                    store.startGroup(convertName(KEY_ID_PROPERTIES));
                    {
                        emitLongArray(store, idProperties, KEY_DEVICE_UUID);
                        emitLongArray(store, idProperties, KEY_DRIVER_UUID);
                        emitLongArray(store, idProperties, KEY_DEVICE_LUID);
                        emitLong(store, idProperties, KEY_DEVICE_NODE_MASK);
                        emitBoolean(store, idProperties, KEY_DEVICE_LUID_VALID);
                    }
                    store.endGroup();

                    JSONObject maintenance3Properties = device.getJSONObject(KEY_MAINTENANCE_3_PROPERTIES);
                    store.startGroup(convertName(KEY_MAINTENANCE_3_PROPERTIES));
                    {
                        emitLong(store, maintenance3Properties, KEY_MAX_PER_SET_DESCRIPTORS);
                        emitString(store, maintenance3Properties, KEY_MAX_MEMORY_ALLOCATION_SIZE);
                    }
                    store.endGroup();

                    JSONObject bit16StorageFeatures = device.getJSONObject(KEY_16BIT_STORAGE_FEATURES);
                    store.startGroup(convertName(KEY_16BIT_STORAGE_FEATURES));
                    {
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_BUFFER_16BIT_ACCESS);
                        emitBoolean(store, bit16StorageFeatures, KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS);
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_PUSH_CONSTANT_16);
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_INPUT_OUTPUT_16);
                    }
                    store.endGroup();

                    JSONObject multiviewFeatures = device.getJSONObject(KEY_MULTIVIEW_FEATURES);
                    store.startGroup(convertName(KEY_MULTIVIEW_FEATURES));
                    {
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW);
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW_GEOMETRY_SHADER);
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW_TESSELLATION_SHADER);
                    }
                    store.endGroup();

                    JSONObject variablePointerFeatures = device.getJSONObject(KEY_VARIABLE_POINTER_FEATURES);
                    store.startGroup(convertName(KEY_VARIABLE_POINTER_FEATURES));
                    {
                        emitBoolean(store, variablePointerFeatures, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        emitBoolean(store, variablePointerFeatures, KEY_VARIABLE_POINTERS);
                    }
                    store.endGroup();

                    JSONObject protectedMemoryFeatures = device.getJSONObject(KEY_PROTECTED_MEMORY_FEATURES);
                    store.startGroup(convertName(KEY_PROTECTED_MEMORY_FEATURES));
                    {
                        emitBoolean(store, protectedMemoryFeatures, KEY_PROTECTED_MEMORY);
                    }
                    store.endGroup();

                    JSONObject samplerYcbcrConversionFeatures = device.getJSONObject(KEY_SAMPLER_YCBCR_CONVERSION_FEATURES);
                    store.startGroup(convertName(KEY_SAMPLER_YCBCR_CONVERSION_FEATURES));
                    {
                        emitBoolean(store, samplerYcbcrConversionFeatures, KEY_SAMPLER_YCBCR_CONVERSION);
                    }
                    store.endGroup();

                    JSONObject shaderDrawParameterFeatures = device.getJSONObject(KEY_SHADER_DRAW_PARAMETER_FEATURES);
                    store.startGroup(convertName(KEY_SHADER_DRAW_PARAMETER_FEATURES));
                    {
                        emitBoolean(store, shaderDrawParameterFeatures, KEY_SHADER_DRAW_PARAMETERS);
                    }
                    store.endGroup();

                    JSONArray externalFences = device.getJSONArray(KEY_EXTERNAL_FENCE_PROPERTIES);
                    store.startArray(convertName(KEY_EXTERNAL_FENCE_PROPERTIES));
                    for (int idx = 0; idx < externalFences.length(); ++idx) {
                        JSONArray externalFencePair = externalFences.getJSONArray(idx);
                        JSONObject externalFenceProperties = externalFencePair.getJSONObject(1);
                        store.startGroup();
                        {
                            store.addResult("handle_type", externalFencePair.getLong(0));
                            emitLong(store, externalFenceProperties, KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES);
                            emitLong(store, externalFenceProperties, KEY_COMPATIBLE_HANDLE_TYPES);
                            emitLong(store, externalFenceProperties, KEY_EXTERNAL_FENCE_FEATURES);
                        }
                        store.endGroup();
                    }
                    store.endArray();

                    JSONArray externalSemaphores = device.getJSONArray(KEY_EXTERNAL_SEMAPHORE_PROPERTIES);
                    store.startArray(convertName(KEY_EXTERNAL_SEMAPHORE_PROPERTIES));
                    for (int idx = 0; idx < externalSemaphores.length(); ++idx) {
                        JSONArray externalSemaphorePair = externalSemaphores.getJSONArray(idx);
                        JSONObject externalSemaphoreProperties = externalSemaphorePair.getJSONObject(1);
                        store.startGroup();
                        {
                            store.addResult("handle_type", externalSemaphorePair.getLong(0));
                            emitLong(store, externalSemaphoreProperties, KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_COMPATIBLE_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_EXTERNAL_SEMAPHORE_FEATURES);
                        }
                        store.endGroup();
                    }
                    store.endArray();
                }
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitLayers(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray layers = parent.getJSONArray(KEY_LAYERS);
        store.startArray(convertName(KEY_LAYERS));
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            store.startGroup();
            {
                JSONObject properties = layer.getJSONObject(KEY_PROPERTIES);
                store.startGroup(convertName(KEY_PROPERTIES));
                {
                    emitString(store, properties, KEY_LAYER_NAME);
                    emitLong(store, properties, KEY_SPEC_VERSION);
                    emitLong(store, properties, KEY_IMPLEMENTATION_VERSION);
                    emitString(store, properties, KEY_DESCRIPTION);
                }
                store.endGroup();
                emitExtensions(store, layer);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitInstanceApiVersion(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        store.addResult(convertName(KEY_INSTANCE_API_VERSION), parent.getLong(KEY_API_VERSION));
    }

    private static void emitDriverPropertiesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extDriverProperties = parent.getJSONObject(KEY_VK_KHR_DRIVER_PROPERTIES);
            try {
                store.startGroup(convertName(KEY_VK_KHR_DRIVER_PROPERTIES));
                {
                    JSONObject driverPropertiesKHR = extDriverProperties.getJSONObject(KEY_DRIVER_PROPERTIES_KHR);
                    store.startGroup(convertName(KEY_DRIVER_PROPERTIES_KHR));
                    {
                        emitLong(store, driverPropertiesKHR, KEY_DRIVER_ID);
                        emitString(store, driverPropertiesKHR, KEY_DRIVER_NAME);
                        emitString(store, driverPropertiesKHR, KEY_DRIVER_INFO);

                        JSONObject conformanceVersion = driverPropertiesKHR.getJSONObject(KEY_CONFORMANCE_VERSION);
                        store.startGroup(convertName(KEY_CONFORMANCE_VERSION));
                        {
                            emitLong(store, conformanceVersion, KEY_MAJOR);
                            emitLong(store, conformanceVersion, KEY_MINOR);
                            emitLong(store, conformanceVersion, KEY_SUBMINOR);
                            emitLong(store, conformanceVersion, KEY_PATCH);
                        }
                        store.endGroup();
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ignored) {
        }
    }

    private static void emitVariablePointerFeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extVariablePointerFeatures = parent.getJSONObject(KEY_VK_KHR_VARIABLE_POINTERS);
            try {
                store.startGroup(convertName(KEY_VK_KHR_VARIABLE_POINTERS));
                {
                    JSONObject variablePointerFeaturesKHR = extVariablePointerFeatures.getJSONObject(KEY_VARIABLE_POINTER_FEATURES_KHR);
                    store.startGroup(convertName(KEY_VARIABLE_POINTER_FEATURES_KHR));
                    {
                        emitBoolean(store, variablePointerFeaturesKHR, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        emitBoolean(store, variablePointerFeaturesKHR, KEY_VARIABLE_POINTERS);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ignored) {
        }
    }

    private static void emitExtension(String key, DeviceInfoStore store, JSONObject parent)
            throws Exception {
        if (!extensionNameToEnum.containsKey(key)) return;
        switch (extensionNameToEnum.get(key)) {
            case ENUM_VK_KHR_VARIABLE_POINTERS:
              emitVariablePointerFeaturesKHR(store, parent);
              break;
            case ENUM_VK_KHR_DRIVER_PROPERTIES:
              emitDriverPropertiesKHR(store, parent);
              break;
        }
    }

    private static void emitExtensions(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray extensions = parent.getJSONArray(KEY_EXTENSIONS);
        store.startArray(convertName(KEY_EXTENSIONS));
        for (int i = 0; i < extensions.length(); i++) {
            JSONObject extension = extensions.getJSONObject(i);
            store.startGroup();
            {
                emitString(store, extension, KEY_EXTENSION_NAME);
                emitLong(store, extension, KEY_SPEC_VERSION);
            }
            store.endGroup();
        }
        store.endArray();

        for (int i = 0; i < extensions.length(); i++) {
            JSONObject extension = extensions.getJSONObject(i);
            String key = extension.getString(KEY_EXTENSION_NAME);
            emitExtension(key, store, parent);
        }
    }

    private static void emitBoolean(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getInt(name) != 0 ? true : false);
    }

    private static void emitLong(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getLong(name));
    }

    private static void emitDouble(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getDouble(name));
    }

    private static void emitString(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getString(name));
    }

    private static void emitLongArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        long[] array = new long[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getLong(i);
        }
        store.addArrayResult(convertName(name), array);
    }

    private static void emitDoubleArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        double[] array = new double[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getDouble(i);
        }
        store.addArrayResult(convertName(name), array);
    }

    private static String convertName(String name) {
        // This translation could be done algorithmically, but in this case being able to
        // code-search for both the original and converted names is more important.
        switch (name) {
            case KEY_16BIT_STORAGE_FEATURES: return "bit16_storage_features";
            case KEY_ALPHA_TO_ONE: return "alpha_to_one";
            case KEY_API_VERSION: return "api_version";
            case KEY_BUFFER_DEVICE_ADDRESS: return "buffer_device_address";
            case KEY_BUFFER_DEVICE_ADDRESS_CAPTURE_REPLAY: return "buffer_device_address_capture_replay";
            case KEY_BUFFER_DEVICE_ADDRESS_MULTI_DEVICE: return "buffer_device_address_multi_device";
            case KEY_BUFFER_FEATURES: return "buffer_features";
            case KEY_BUFFER_IMAGE_GRANULARITY: return "buffer_image_granularity";
            case KEY_COMPATIBLE_HANDLE_TYPES: return "compatible_handle_types";
            case KEY_COMPUTE_FULL_SUBGROUPS: return "compute_full_subgroups";
            case KEY_CONFORMANCE_VERSION: return "conformance_version";
            case KEY_DENORM_BEHAVIOR_INDEPENDENCE: return "denorm_behavior_independence";
            case KEY_DEPTH: return "depth";
            case KEY_DEPTH_BIAS_CLAMP: return "depth_bias_clamp";
            case KEY_DEPTH_BOUNDS: return "depth_bounds";
            case KEY_DEPTH_CLAMP: return "depth_clamp";
            case KEY_DESCRIPTION: return "description";
            case KEY_DESCRIPTOR_BINDING_INLINE_UNIFORM_BLOCK_UPDATE_AFTER_BIND: return "descriptor_binding_inline_uniform_block_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_PARTIALLY_BOUND: return "descriptor_binding_partially_bound";
            case KEY_DESCRIPTOR_BINDING_SAMPLED_IMAGE_UPDATE_AFTER_BIND: return "descriptor_binding_sampled_image_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_STORAGE_BUFFER_UPDATE_AFTER_BIND: return "descriptor_binding_storage_buffer_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_STORAGE_IMAGE_UPDATE_AFTER_BIND: return "descriptor_binding_storage_image_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_STORAGE_TEXEL_BUFFER_UPDATE_AFTER_BIND: return "descriptor_binding_storage_texel_buffer_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_UNIFORM_BUFFER_UPDATE_AFTER_BIND: return "descriptor_binding_uniform_buffer_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_UNIFORM_TEXEL_BUFFER_UPDATE_AFTER_BIND: return "descriptor_binding_uniform_texel_buffer_update_after_bind";
            case KEY_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING: return "descriptor_binding_update_unused_while_pending";
            case KEY_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT: return "descriptor_binding_variable_descriptor_count";
            case KEY_DESCRIPTOR_INDEXING: return "descriptor_indexing";
            case KEY_DEVICES: return "devices";
            case KEY_DEVICE_GROUPS: return "device_groups";
            case KEY_DEVICE_ID: return "device_id";
            case KEY_DEVICE_LUID: return "device_luid";
            case KEY_DEVICE_LUID_VALID: return "device_luid_valid";
            case KEY_DEVICE_NAME: return "device_name";
            case KEY_DEVICE_NODE_MASK: return "device_node_mask";
            case KEY_DEVICE_TYPE: return "device_type";
            case KEY_DEVICE_UUID: return "device_uuid";
            case KEY_DISCRETE_QUEUE_PRIORITIES: return "discrete_queue_priorities";
            case KEY_DRAW_INDIRECT_COUNT: return "draw_indirect_count";
            case KEY_DRAW_INDIRECT_FIRST_INSTANCE: return "draw_indirect_first_instance";
            case KEY_DRIVER_ID: return "driver_id";
            case KEY_DRIVER_INFO: return "driver_info";
            case KEY_DRIVER_NAME: return "driver_name";
            case KEY_DRIVER_PROPERTIES_KHR: return "driver_properties_khr";
            case KEY_DRIVER_UUID: return "driver_uuid";
            case KEY_DRIVER_VERSION: return "driver_version";
            case KEY_DUAL_SRC_BLEND: return "dual_src_blend";
            case KEY_DYNAMIC_RENDERING: return "dynamic_rendering";
            case KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES: return "export_from_imported_handle_types";
            case KEY_EXTENSIONS: return "extensions";
            case KEY_EXTENSION_NAME: return "extension_name";
            case KEY_EXTERNAL_FENCE_FEATURES: return "external_fence_features";
            case KEY_EXTERNAL_FENCE_PROPERTIES: return "external_fence_properties";
            case KEY_EXTERNAL_SEMAPHORE_FEATURES: return "external_semaphore_features";
            case KEY_EXTERNAL_SEMAPHORE_PROPERTIES: return "external_semaphore_properties";
            case KEY_FEATURES: return "features";
            case KEY_FILL_MODE_NON_SOLID: return "fill_mode_non_solid";
            case KEY_FILTER_MINMAX_IMAGE_COMPONENT_MAPPING: return "filter_minmax_image_component_mapping";
            case KEY_FILTER_MINMAX_SINGLE_COMPONENT_FORMATS: return "filter_minmax_single_component_formats";
            case KEY_FLAGS: return "flags";
            case KEY_FORMATS: return "formats";
            case KEY_FRAGMENT_STORES_AND_ATOMICS: return "fragment_stores_and_atomics";
            case KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS: return "framebuffer_color_sample_counts";
            case KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS: return "framebuffer_depth_sample_counts";
            case KEY_FRAMEBUFFER_INTEGER_COLOR_SAMPLE_COUNTS: return "framebuffer_integer_color_sample_counts";
            case KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS: return "framebuffer_no_attachments_sample_counts";
            case KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS: return "framebuffer_stencil_sample_counts";
            case KEY_FULL_DRAW_INDEX_UINT32: return "full_draw_index_uint32";
            case KEY_GEOMETRY_SHADER: return "geometry_shader";
            case KEY_HEAP_INDEX: return "heap_index";
            case KEY_HEIGHT: return "height";
            case KEY_HOST_QUERY_RESET: return "host_query_reset";
            case KEY_ID_PROPERTIES: return "id_properties";
            case KEY_IMAGELESS_FRAMEBUFFER: return "imageless_framebuffer";
            case KEY_IMAGE_CUBE_ARRAY: return "image_cube_array";
            case KEY_IMPLEMENTATION_VERSION: return "implementation_version";
            case KEY_INDEPENDENT_BLEND: return "independent_blend";
            case KEY_INDEPENDENT_RESOLVE: return "independent_resolve";
            case KEY_INDEPENDENT_RESOLVE_NONE: return "independent_resolve_none";
            case KEY_INHERITED_QUERIES: return "inherited_queries";
            case KEY_INLINE_UNIFORM_BLOCK: return "inline_uniform_block";
            case KEY_INSTANCE_API_VERSION: return "instance_api_version";
            case KEY_INTEGER_DOT_PRODUCT_16BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_16bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_16BIT_SIGNED_ACCELERATED: return "integer_dot_product_16bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_16BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_16bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_32BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_32bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_32BIT_SIGNED_ACCELERATED: return "integer_dot_product_32bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_32BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_32bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_4x8bit_packed_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_SIGNED_ACCELERATED: return "integer_dot_product_4x8bit_packed_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_UNSIGNED_ACCELERATED: return "integer_dot_product_4x8bit_packed_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_64BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_64bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_64BIT_SIGNED_ACCELERATED: return "integer_dot_product_64bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_64BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_64bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_8BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_8bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_8BIT_SIGNED_ACCELERATED: return "integer_dot_product_8bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_8BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_8bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_16bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_SIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_16bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_16bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_32bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_SIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_32bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_32bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_4x8bit_packed_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_SIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_4x8bit_packed_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_UNSIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_4x8bit_packed_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_64bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_64bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_64bit_unsigned_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_MIXED_SIGNEDNESS_ACCELERATED: return "integer_dot_product_accumulating_saturating_8bit_mixed_signedness_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_SIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_8bit_signed_accelerated";
            case KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_UNSIGNED_ACCELERATED: return "integer_dot_product_accumulating_saturating_8bit_unsigned_accelerated";
            case KEY_LARGE_POINTS: return "large_points";
            case KEY_LAYERS: return "layers";
            case KEY_LAYER_NAME: return "layer_name";
            case KEY_LIMITS: return "limits";
            case KEY_LINEAR_TILING_FEATURES: return "linear_tiling_features";
            case KEY_LINE_WIDTH_GRANULARITY: return "line_width_granularity";
            case KEY_LINE_WIDTH_RANGE: return "line_width_range";
            case KEY_LOGIC_OP: return "logic_op";
            case KEY_MAINTENANCE4: return "maintenance4";
            case KEY_MAINTENANCE_3_PROPERTIES: return "maintenance_3_properties";
            case KEY_MAJOR: return "major";
            case KEY_MAX_BOUND_DESCRIPTOR_SETS: return "max_bound_descriptor_sets";
            case KEY_MAX_BUFFER_SIZE: return "max_buffer_size";
            case KEY_MAX_CLIP_DISTANCES: return "max_clip_distances";
            case KEY_MAX_COLOR_ATTACHMENTS: return "max_color_attachments";
            case KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES: return "max_combined_clip_and_cull_distances";
            case KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE: return "max_compute_shared_memory_size";
            case KEY_MAX_COMPUTE_WORKGROUP_SUBGROUPS: return "max_compute_workgroup_subgroups";
            case KEY_MAX_COMPUTE_WORK_GROUP_COUNT: return "max_compute_work_group_count";
            case KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS: return "max_compute_work_group_invocations";
            case KEY_MAX_COMPUTE_WORK_GROUP_SIZE: return "max_compute_work_group_size";
            case KEY_MAX_CULL_DISTANCES: return "max_cull_distances";
            case KEY_MAX_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCKS: return "max_descriptor_set_inline_uniform_blocks";
            case KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS: return "max_descriptor_set_input_attachments";
            case KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES: return "max_descriptor_set_sampled_images";
            case KEY_MAX_DESCRIPTOR_SET_SAMPLERS: return "max_descriptor_set_samplers";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS: return "max_descriptor_set_storage_buffers";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC: return "max_descriptor_set_storage_buffers_dynamic";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES: return "max_descriptor_set_storage_images";
            case KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS: return "max_descriptor_set_uniform_buffers";
            case KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC: return "max_descriptor_set_uniform_buffers_dynamic";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS: return "max_descriptor_set_update_after_bind_inline_uniform_blocks";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS: return "max_descriptor_set_update_after_bind_input_attachments";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLED_IMAGES: return "max_descriptor_set_update_after_bind_sampled_images";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLERS: return "max_descriptor_set_update_after_bind_samplers";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS: return "max_descriptor_set_update_after_bind_storage_buffers";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS_DYNAMIC: return "max_descriptor_set_update_after_bind_storage_buffers_dynamic";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_IMAGES: return "max_descriptor_set_update_after_bind_storage_images";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS: return "max_descriptor_set_update_after_bind_uniform_buffers";
            case KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS_DYNAMIC: return "max_descriptor_set_update_after_bind_uniform_buffers_dynamic";
            case KEY_MAX_DRAW_INDEXED_INDEX_VALUE: return "max_draw_indexed_index_value";
            case KEY_MAX_DRAW_INDIRECT_COUNT: return "max_draw_indirect_count";
            case KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES: return "max_fragment_combined_output_resources";
            case KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS: return "max_fragment_dual_src_attachments";
            case KEY_MAX_FRAGMENT_INPUT_COMPONENTS: return "max_fragment_input_components";
            case KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS: return "max_fragment_output_attachments";
            case KEY_MAX_FRAMEBUFFER_HEIGHT: return "max_framebuffer_height";
            case KEY_MAX_FRAMEBUFFER_LAYERS: return "max_framebuffer_layers";
            case KEY_MAX_FRAMEBUFFER_WIDTH: return "max_framebuffer_width";
            case KEY_MAX_GEOMETRY_INPUT_COMPONENTS: return "max_geometry_input_components";
            case KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS: return "max_geometry_output_components";
            case KEY_MAX_GEOMETRY_OUTPUT_VERTICES: return "max_geometry_output_vertices";
            case KEY_MAX_GEOMETRY_SHADER_INVOCATIONS: return "max_geometry_shader_invocations";
            case KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS: return "max_geometry_total_output_components";
            case KEY_MAX_IMAGE_ARRAY_LAYERS: return "max_image_array_layers";
            case KEY_MAX_IMAGE_DIMENSION_1D: return "max_image_dimension_1d";
            case KEY_MAX_IMAGE_DIMENSION_2D: return "max_image_dimension_2d";
            case KEY_MAX_IMAGE_DIMENSION_3D: return "max_image_dimension_3d";
            case KEY_MAX_IMAGE_DIMENSION_CUBE: return "max_image_dimension_cube";
            case KEY_MAX_INLINE_UNIFORM_BLOCK_SIZE: return "max_inline_uniform_block_size";
            case KEY_MAX_INLINE_UNIFORM_TOTAL_SIZE: return "max_inline_uniform_total_size";
            case KEY_MAX_INTERPOLATION_OFFSET: return "max_interpolation_offset";
            case KEY_MAX_MEMORY_ALLOCATION_COUNT: return "max_memory_allocation_count";
            case KEY_MAX_MEMORY_ALLOCATION_SIZE: return "max_memory_allocation_size";
            case KEY_MAX_MULTIVIEW_INSTANCE_INDEX: return "max_multiview_instance_index";
            case KEY_MAX_MULTIVIEW_VIEW_COUNT: return "max_multiview_view_count";
            case KEY_MAX_PER_SET_DESCRIPTORS: return "max_per_set_descriptors";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_INLINE_UNIFORM_BLOCKS: return "max_per_stage_descriptor_inline_uniform_blocks";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS: return "max_per_stage_descriptor_input_attachments";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES: return "max_per_stage_descriptor_sampled_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS: return "max_per_stage_descriptor_samplers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS: return "max_per_stage_descriptor_storage_buffers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES: return "max_per_stage_descriptor_storage_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS: return "max_per_stage_descriptor_uniform_buffers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS: return "max_per_stage_descriptor_update_after_bind_inline_uniform_blocks";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS: return "max_per_stage_descriptor_update_after_bind_input_attachments";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLED_IMAGES: return "max_per_stage_descriptor_update_after_bind_sampled_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLERS: return "max_per_stage_descriptor_update_after_bind_samplers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_BUFFERS: return "max_per_stage_descriptor_update_after_bind_storage_buffers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_IMAGES: return "max_per_stage_descriptor_update_after_bind_storage_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_UNIFORM_BUFFERS: return "max_per_stage_descriptor_update_after_bind_uniform_buffers";
            case KEY_MAX_PER_STAGE_RESOURCES: return "max_per_stage_resources";
            case KEY_MAX_PER_STAGE_UPDATE_AFTER_BIND_RESOURCES: return "max_per_stage_update_after_bind_resources";
            case KEY_MAX_PUSH_CONSTANTS_SIZE: return "max_push_constants_size";
            case KEY_MAX_SAMPLER_ALLOCATION_COUNT: return "max_sampler_allocation_count";
            case KEY_MAX_SAMPLER_ANISOTROPY: return "max_sampler_anisotropy";
            case KEY_MAX_SAMPLER_LOD_BIAS: return "max_sampler_lod_bias";
            case KEY_MAX_SAMPLE_MASK_WORDS: return "max_sample_mask_words";
            case KEY_MAX_STORAGE_BUFFER_RANGE: return "max_storage_buffer_range";
            case KEY_MAX_SUBGROUP_SIZE: return "max_subgroup_size";
            case KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS: return "max_tessellation_control_per_patch_output_components";
            case KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS: return "max_tessellation_control_per_vertex_input_components";
            case KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS: return "max_tessellation_control_per_vertex_output_components";
            case KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS: return "max_tessellation_control_total_output_components";
            case KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS: return "max_tessellation_evaluation_input_components";
            case KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS: return "max_tessellation_evaluation_output_components";
            case KEY_MAX_TESSELLATION_GENERATION_LEVEL: return "max_tessellation_generation_level";
            case KEY_MAX_TESSELLATION_PATCH_SIZE: return "max_tessellation_patch_size";
            case KEY_MAX_TEXEL_BUFFER_ELEMENTS: return "max_texel_buffer_elements";
            case KEY_MAX_TEXEL_GATHER_OFFSET: return "max_texel_gather_offset";
            case KEY_MAX_TEXEL_OFFSET: return "max_texel_offset";
            case KEY_MAX_TIMELINE_SEMAPHORE_VALUE_DIFFERENCE: return "max_timeline_semaphore_value_difference";
            case KEY_MAX_UNIFORM_BUFFER_RANGE: return "max_uniform_buffer_range";
            case KEY_MAX_UPDATE_AFTER_BIND_DESCRIPTORS_IN_ALL_POOLS: return "max_update_after_bind_descriptors_in_all_pools";
            case KEY_MAX_VERTEX_INPUT_ATTRIBUTES: return "max_vertex_input_attributes";
            case KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET: return "max_vertex_input_attribute_offset";
            case KEY_MAX_VERTEX_INPUT_BINDINGS: return "max_vertex_input_bindings";
            case KEY_MAX_VERTEX_INPUT_BINDING_STRIDE: return "max_vertex_input_binding_stride";
            case KEY_MAX_VERTEX_OUTPUT_COMPONENTS: return "max_vertex_output_components";
            case KEY_MAX_VIEWPORTS: return "max_viewports";
            case KEY_MAX_VIEWPORT_DIMENSIONS: return "max_viewport_dimensions";
            case KEY_MEMORY: return "memory";
            case KEY_MEMORY_HEAPS: return "memory_heaps";
            case KEY_MEMORY_HEAP_COUNT: return "memory_heap_count";
            case KEY_MEMORY_TYPES: return "memory_types";
            case KEY_MEMORY_TYPE_COUNT: return "memory_type_count";
            case KEY_MINOR: return "minor";
            case KEY_MIN_IMAGE_TRANSFER_GRANULARITY: return "min_image_transfer_granularity";
            case KEY_MIN_INTERPOLATION_OFFSET: return "min_interpolation_offset";
            case KEY_MIN_MEMORY_MAP_ALIGNMENT: return "min_memory_map_alignment";
            case KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT: return "min_storage_buffer_offset_alignment";
            case KEY_MIN_SUBGROUP_SIZE: return "min_subgroup_size";
            case KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT: return "min_texel_buffer_offset_alignment";
            case KEY_MIN_TEXEL_GATHER_OFFSET: return "min_texel_gather_offset";
            case KEY_MIN_TEXEL_OFFSET: return "min_texel_offset";
            case KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT: return "min_uniform_buffer_offset_alignment";
            case KEY_MIPMAP_PRECISION_BITS: return "mipmap_precision_bits";
            case KEY_MULTIVIEW: return "multiview";
            case KEY_MULTIVIEW_FEATURES: return "multiview_features";
            case KEY_MULTIVIEW_GEOMETRY_SHADER: return "multiview_geometry_shader";
            case KEY_MULTIVIEW_PROPERTIES: return "multiview_properties";
            case KEY_MULTIVIEW_TESSELLATION_SHADER: return "multiview_tessellation_shader";
            case KEY_MULTI_DRAW_INDIRECT: return "multi_draw_indirect";
            case KEY_MULTI_VIEWPORT: return "multi_viewport";
            case KEY_NON_COHERENT_ATOM_SIZE: return "non_coherent_atom_size";
            case KEY_OCCLUSION_QUERY_PRECISE: return "occlusion_query_precise";
            case KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT: return "optimal_buffer_copy_offset_alignment";
            case KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT: return "optimal_buffer_copy_row_pitch_alignment";
            case KEY_OPTIMAL_TILING_FEATURES: return "optimal_tiling_features";
            case KEY_PATCH: return "patch";
            case KEY_PIPELINE_CACHE_UUID: return "pipeline_cache_uuid";
            case KEY_PIPELINE_CREATION_CACHE_CONTROL: return "pipeline_creation_cache_control";
            case KEY_PIPELINE_STATISTICS_QUERY: return "pipeline_statistics_query";
            case KEY_POINT_CLIPPING_BEHAVIOR: return "point_clipping_behavior";
            case KEY_POINT_CLIPPING_PROPERTIES: return "point_clipping_properties";
            case KEY_POINT_SIZE_GRANULARITY: return "point_size_granularity";
            case KEY_POINT_SIZE_RANGE: return "point_size_range";
            case KEY_PRIVATE_DATA: return "private_data";
            case KEY_PROPERTIES: return "properties";
            case KEY_PROPERTY_FLAGS: return "property_flags";
            case KEY_PROTECTED_MEMORY: return "protected_memory";
            case KEY_PROTECTED_MEMORY_FEATURES: return "protected_memory_features";
            case KEY_QUAD_DIVERGENT_IMPLICIT_LOD: return "quad_divergent_implicit_lod";
            case KEY_QUAD_OPERATIONS_IN_ALL_STAGES: return "quad_operations_in_all_stages";
            case KEY_QUEUES: return "queues";
            case KEY_QUEUE_COUNT: return "queue_count";
            case KEY_QUEUE_FLAGS: return "queue_flags";
            case KEY_REQUIRED_SUBGROUP_SIZE_STAGES: return "required_subgroup_size_stages";
            case KEY_RESIDENCY_ALIGNED_MIP_SIZE: return "residency_aligned_mip_size";
            case KEY_RESIDENCY_NON_RESIDENT_STRICT: return "residency_non_resident_strict";
            case KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE: return "residency_standard_2d_block_shape";
            case KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE: return "residency_standard_2d_multisample_block_shape";
            case KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE: return "residency_standard_3d_block_shape";
            case KEY_ROBUST_BUFFER_ACCESS: return "robust_buffer_access";
            case KEY_ROBUST_BUFFER_ACCESS_UPDATE_AFTER_BIND: return "robust_buffer_access_update_after_bind";
            case KEY_ROBUST_IMAGE_ACCESS: return "robust_image_access";
            case KEY_ROUNDING_MODE_INDEPENDENCE: return "rounding_mode_independence";
            case KEY_RUNTIME_DESCRIPTOR_ARRAY: return "runtime_descriptor_array";
            case KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS: return "sampled_image_color_sample_counts";
            case KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS: return "sampled_image_depth_sample_counts";
            case KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS: return "sampled_image_integer_sample_counts";
            case KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS: return "sampled_image_stencil_sample_counts";
            case KEY_SAMPLER_ANISOTROPY: return "sampler_anisotropy";
            case KEY_SAMPLER_FILTER_MINMAX: return "sampler_filter_minmax";
            case KEY_SAMPLER_MIRROR_CLAMP_TO_EDGE: return "sampler_mirror_clamp_to_edge";
            case KEY_SAMPLER_YCBCR_CONVERSION: return "sampler_ycbcr_conversion";
            case KEY_SAMPLER_YCBCR_CONVERSION_FEATURES: return "sampler_ycbcr_conversion_features";
            case KEY_SAMPLE_RATE_SHADING: return "sample_rate_shading";
            case KEY_SCALAR_BLOCK_LAYOUT: return "scalar_block_layout";
            case KEY_SEPARATE_DEPTH_STENCIL_LAYOUTS: return "separate_depth_stencil_layouts";
            case KEY_SHADER_BUFFER_INT64_ATOMICS: return "shader_buffer_int64_atomics";
            case KEY_SHADER_CLIP_DISTANCE: return "shader_clip_distance";
            case KEY_SHADER_CULL_DISTANCE: return "shader_cull_distance";
            case KEY_SHADER_DEMOTE_TO_HELPER_INVOCATION: return "shader_demote_to_helper_invocation";
            case KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16: return "shader_denorm_flush_to_zero_float16";
            case KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32: return "shader_denorm_flush_to_zero_float32";
            case KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64: return "shader_denorm_flush_to_zero_float64";
            case KEY_SHADER_DENORM_PRESERVE_FLOAT16: return "shader_denorm_preserve_float16";
            case KEY_SHADER_DENORM_PRESERVE_FLOAT32: return "shader_denorm_preserve_float32";
            case KEY_SHADER_DENORM_PRESERVE_FLOAT64: return "shader_denorm_preserve_float64";
            case KEY_SHADER_DRAW_PARAMETERS: return "shader_draw_parameters";
            case KEY_SHADER_DRAW_PARAMETER_FEATURES: return "shader_draw_parameter_features";
            case KEY_SHADER_FLOAT16: return "shader_float16";
            case KEY_SHADER_FLOAT64: return "shader_float64";
            case KEY_SHADER_IMAGE_GATHER_EXTENDED: return "shader_image_gather_extended";
            case KEY_SHADER_INPUT_ATTACHMENT_ARRAY_DYNAMIC_INDEXING: return "shader_input_attachment_array_dynamic_indexing";
            case KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NONUNIFORM_INDEXING_NATIVE: return "shader_input_attachment_array_nonuniform_indexing_native";
            case KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NON_UNIFORM_INDEXING: return "shader_input_attachment_array_non_uniform_indexing";
            case KEY_SHADER_INT16: return "shader_int16";
            case KEY_SHADER_INT64: return "shader_int64";
            case KEY_SHADER_INT8: return "shader_int8";
            case KEY_SHADER_INTEGER_DOT_PRODUCT: return "shader_integer_dot_product";
            case KEY_SHADER_OUTPUT_LAYER: return "shader_output_layer";
            case KEY_SHADER_OUTPUT_VIEWPORT_INDEX: return "shader_output_viewport_index";
            case KEY_SHADER_RESOURCE_MIN_LOD: return "shader_resource_min_lod";
            case KEY_SHADER_RESOURCE_RESIDENCY: return "shader_resource_residency";
            case KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16: return "shader_rounding_mode_rte_float16";
            case KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32: return "shader_rounding_mode_rte_float32";
            case KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64: return "shader_rounding_mode_rte_float64";
            case KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16: return "shader_rounding_mode_rtz_float16";
            case KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32: return "shader_rounding_mode_rtz_float32";
            case KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64: return "shader_rounding_mode_rtz_float64";
            case KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING: return "shader_sampled_image_array_dynamic_indexing";
            case KEY_SHADER_SAMPLED_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE: return "shader_sampled_image_array_nonuniform_indexing_native";
            case KEY_SHADER_SAMPLED_IMAGE_ARRAY_NON_UNIFORM_INDEXING: return "shader_sampled_image_array_non_uniform_indexing";
            case KEY_SHADER_SHARED_INT64_ATOMICS: return "shader_shared_int64_atomics";
            case KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16: return "shader_signed_zero_inf_nan_preserve_float16";
            case KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32: return "shader_signed_zero_inf_nan_preserve_float32";
            case KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64: return "shader_signed_zero_inf_nan_preserve_float64";
            case KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_storage_buffer_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE: return "shader_storage_buffer_array_nonuniform_indexing_native";
            case KEY_SHADER_STORAGE_BUFFER_ARRAY_NON_UNIFORM_INDEXING: return "shader_storage_buffer_array_non_uniform_indexing";
            case KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING: return "shader_storage_image_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE: return "shader_storage_image_array_nonuniform_indexing_native";
            case KEY_SHADER_STORAGE_IMAGE_ARRAY_NON_UNIFORM_INDEXING: return "shader_storage_image_array_non_uniform_indexing";
            case KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS: return "shader_storage_image_extended_formats";
            case KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE: return "shader_storage_image_multisample";
            case KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT: return "shader_storage_image_read_without_format";
            case KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT: return "shader_storage_image_write_without_format";
            case KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_storage_texel_buffer_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING: return "shader_storage_texel_buffer_array_non_uniform_indexing";
            case KEY_SHADER_SUBGROUP_EXTENDED_TYPES: return "shader_subgroup_extended_types";
            case KEY_SHADER_TERMINATE_INVOCATION: return "shader_terminate_invocation";
            case KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE: return "shader_tessellation_and_geometry_point_size";
            case KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_uniform_buffer_array_dynamic_indexing";
            case KEY_SHADER_UNIFORM_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE: return "shader_uniform_buffer_array_nonuniform_indexing_native";
            case KEY_SHADER_UNIFORM_BUFFER_ARRAY_NON_UNIFORM_INDEXING: return "shader_uniform_buffer_array_non_uniform_indexing";
            case KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_uniform_texel_buffer_array_dynamic_indexing";
            case KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING: return "shader_uniform_texel_buffer_array_non_uniform_indexing";
            case KEY_SHADER_ZERO_INITIALIZE_WORKGROUP_MEMORY: return "shader_zero_initialize_workgroup_memory";
            case KEY_SIZE: return "size";
            case KEY_SPARSE_ADDRESS_SPACE_SIZE: return "sparse_address_space_size";
            case KEY_SPARSE_BINDING: return "sparse_binding";
            case KEY_SPARSE_PROPERTIES: return "sparse_properties";
            case KEY_SPARSE_RESIDENCY_16_SAMPLES: return "sparse_residency_16_samples";
            case KEY_SPARSE_RESIDENCY_2_SAMPLES: return "sparse_residency_2_samples";
            case KEY_SPARSE_RESIDENCY_4_SAMPLES: return "sparse_residency_4_samples";
            case KEY_SPARSE_RESIDENCY_8_SAMPLES: return "sparse_residency_8_samples";
            case KEY_SPARSE_RESIDENCY_ALIASED: return "sparse_residency_aliased";
            case KEY_SPARSE_RESIDENCY_BUFFER: return "sparse_residency_buffer";
            case KEY_SPARSE_RESIDENCY_IMAGE_2D: return "sparse_residency_image_2d";
            case KEY_SPARSE_RESIDENCY_IMAGE_3D: return "sparse_residency_image_3d";
            case KEY_SPEC_VERSION: return "spec_version";
            case KEY_STANDARD_SAMPLE_LOCATIONS: return "standard_sample_locations";
            case KEY_STORAGE_BUFFER_16BIT_ACCESS: return "storage_buffer_16bit_access";
            case KEY_STORAGE_BUFFER_8BIT_ACCESS: return "storage_buffer_8bit_access";
            case KEY_STORAGE_IMAGE_SAMPLE_COUNTS: return "storage_image_sample_counts";
            case KEY_STORAGE_INPUT_OUTPUT_16: return "storage_input_output_16";
            case KEY_STORAGE_PUSH_CONSTANT8: return "storage_push_constant8";
            case KEY_STORAGE_PUSH_CONSTANT_16: return "storage_push_constant_16";
            case KEY_STORAGE_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES: return "storage_texel_buffer_offset_alignment_bytes";
            case KEY_STORAGE_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT: return "storage_texel_buffer_offset_single_texel_alignment";
            case KEY_STRICT_LINES: return "strict_lines";
            case KEY_SUBGROUP_BROADCAST_DYNAMIC_ID: return "subgroup_broadcast_dynamic_id";
            case KEY_SUBGROUP_PROPERTIES: return "subgroup_properties";
            case KEY_SUBGROUP_SIZE: return "subgroup_size";
            case KEY_SUBGROUP_SIZE_CONTROL: return "subgroup_size_control";
            case KEY_SUBMINOR: return "subminor";
            case KEY_SUBSET_ALLOCATION: return "subset_allocation";
            case KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS: return "sub_pixel_interpolation_offset_bits";
            case KEY_SUB_PIXEL_PRECISION_BITS: return "sub_pixel_precision_bits";
            case KEY_SUB_TEXEL_PRECISION_BITS: return "sub_texel_precision_bits";
            case KEY_SUPPORTED_DEPTH_RESOLVE_MODES: return "supported_depth_resolve_modes";
            case KEY_SUPPORTED_OPERATIONS: return "supported_operations";
            case KEY_SUPPORTED_STAGES: return "supported_stages";
            case KEY_SUPPORTED_STENCIL_RESOLVE_MODES: return "supported_stencil_resolve_modes";
            case KEY_SYNCHRONIZATION2: return "synchronization2";
            case KEY_TESSELLATION_SHADER: return "tessellation_shader";
            case KEY_TEXTURE_COMPRESSION_ASTC_HDR: return "texture_compression_astc_hdr";
            case KEY_TEXTURE_COMPRESSION_ASTC_LDR: return "texture_compression_astc_ldr";
            case KEY_TEXTURE_COMPRESSION_BC: return "texture_compression_bc";
            case KEY_TEXTURE_COMPRESSION_ETC2: return "texture_compression_etc2";
            case KEY_TIMELINE_SEMAPHORE: return "timeline_semaphore";
            case KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS: return "timestamp_compute_and_graphics";
            case KEY_TIMESTAMP_PERIOD: return "timestamp_period";
            case KEY_TIMESTAMP_VALID_BITS: return "timestamp_valid_bits";
            case KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS: return "uniform_and_storage_buffer_16bit_access";
            case KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS: return "uniform_and_storage_buffer_8bit_access";
            case KEY_UNIFORM_BUFFER_STANDARD_LAYOUT: return "uniform_buffer_standard_layout";
            case KEY_UNIFORM_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES: return "uniform_texel_buffer_offset_alignment_bytes";
            case KEY_UNIFORM_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT: return "uniform_texel_buffer_offset_single_texel_alignment";
            case KEY_VARIABLE_MULTISAMPLE_RATE: return "variable_multisample_rate";
            case KEY_VARIABLE_POINTERS: return "variable_pointers";
            case KEY_VARIABLE_POINTERS_STORAGE_BUFFER: return "variable_pointers_storage_buffer";
            case KEY_VARIABLE_POINTER_FEATURES: return "variable_pointer_features";
            case KEY_VARIABLE_POINTER_FEATURES_KHR: return "variable_pointer_features_khr";
            case KEY_VENDOR_ID: return "vendor_id";
            case KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS: return "vertex_pipeline_stores_and_atomics";
            case KEY_VIEWPORT_BOUNDS_RANGE: return "viewport_bounds_range";
            case KEY_VIEWPORT_SUB_PIXEL_BITS: return "viewport_sub_pixel_bits";
            case KEY_VK_KHR_DRIVER_PROPERTIES: return "vk_khr_driver_properties";
            case KEY_VK_KHR_VARIABLE_POINTERS: return "vk_khr_variable_pointers";
            case KEY_VULKAN_12_FEATURES: return "vulkan_12_features";
            case KEY_VULKAN_12_PROPERTIES: return "vulkan_12_properties";
            case KEY_VULKAN_13_FEATURES: return "vulkan_13_features";
            case KEY_VULKAN_13_PROPERTIES: return "vulkan_13_properties";
            case KEY_VULKAN_MEMORY_MODEL: return "vulkan_memory_model";
            case KEY_VULKAN_MEMORY_MODEL_AVAILABILITY_VISIBILITY_CHAINS: return "vulkan_memory_model_availability_visibility_chains";
            case KEY_VULKAN_MEMORY_MODEL_DEVICE_SCOPE: return "vulkan_memory_model_device_scope";
            case KEY_WIDE_LINES: return "wide_lines";
            case KEY_WIDTH: return "width";
            default: throw new RuntimeException("unknown key name: " + name);
        }
    }

    private static native String nativeGetVkJSON();

}
