#include "native_startup_evidence.h"

#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

/* This cold harness-owned check attributes the library actually resolved by
 * the process loader. It does not rediscover any Core configuration fact.
 * The caller owns bounded copied text; loader-owned strings are not retained. */
int native_startup_image_observe(native_startup_image_t* image) {
  memset(image, 0, sizeof(*image));
  image->checked = 1;
#if defined(_WIN32)
  {
    HMODULE module = GetModuleHandleA("libcoakka_runtime_v2.dll");
    DWORD length;
    if (module == NULL) module = GetModuleHandleA("coakka_runtime_v2.dll");
    if (module == NULL || GetProcAddress(module, "coakka_v2_runtime_get_core_info") == NULL)
      return 0;
    length = GetModuleFileNameA(module, image->path, (DWORD)sizeof(image->path));
    if (length == 0u || length >= sizeof(image->path)) {
      image->path[0] = '\0';
      return 0;
    }
  }
#else
  {
    Dl_info information;
    void* symbol = dlsym(RTLD_DEFAULT, "coakka_v2_runtime_get_core_info");
    size_t length;
    if (symbol == NULL || dladdr(symbol, &information) == 0 || information.dli_fname == NULL)
      return 0;
    length = strnlen(information.dli_fname, sizeof(image->path));
    if (length == 0u || length >= sizeof(image->path)) return 0;
    memcpy(image->path, information.dli_fname, length + 1u);
  }
#endif
  return 1;
}
