#include <jni.h>
#include <vulkan/vulkan.h>

#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

namespace {

std::string resultMessage(const char* operation, VkResult result) {
    std::ostringstream message;
    message << operation << " failed (VkResult " << static_cast<int>(result) << ")";
    return message.str();
}

const char* deviceTypeName(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU:
            return "integrated GPU";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:
            return "discrete GPU";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:
            return "virtual GPU";
        case VK_PHYSICAL_DEVICE_TYPE_CPU:
            return "CPU renderer";
        default:
            return "other device";
    }
}

std::string enumerateDevices() {
    VkApplicationInfo applicationInfo{};
    applicationInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    applicationInfo.pApplicationName = "PocketDeX capability probe";
    applicationInfo.applicationVersion = 1;
    applicationInfo.pEngineName = "PocketDeX";
    applicationInfo.engineVersion = 1;
    applicationInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &applicationInfo;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        return resultMessage("Vulkan instance creation", result);
    }

    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return resultMessage("Vulkan device enumeration", result);
    }
    if (deviceCount == 0) {
        vkDestroyInstance(instance, nullptr);
        return "No Vulkan physical device reported by the system driver.";
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return resultMessage("Vulkan device enumeration", result);
    }

    std::ostringstream summary;
    for (uint32_t index = 0; index < deviceCount; ++index) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[index], &properties);

        if (index > 0) {
            summary << '\n';
        }
        summary << properties.deviceName << " (" << deviceTypeName(properties.deviceType)
                << "), vendor 0x" << std::hex << std::setw(4) << std::setfill('0')
                << properties.vendorID << ", device 0x" << std::setw(4)
                << properties.deviceID << std::dec << ", Vulkan API "
                << VK_VERSION_MAJOR(properties.apiVersion) << '.'
                << VK_VERSION_MINOR(properties.apiVersion) << '.'
                << VK_VERSION_PATCH(properties.apiVersion);
    }

    vkDestroyInstance(instance, nullptr);
    return summary.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdex_launcher_runtime_VulkanDeviceProbe_queryNative(JNIEnv* env, jclass) {
    const std::string summary = enumerateDevices();
    return env->NewStringUTF(summary.c_str());
}
