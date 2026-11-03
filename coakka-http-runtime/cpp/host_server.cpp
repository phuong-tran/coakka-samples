#include "../native/host-server/example.h"

#include <cstdlib>
#include <exception>

namespace {
class Service final {
 public:
  Service() = default;
  Service(const Service &) = delete;
  Service &operator=(const Service &) = delete;

  ~Service() noexcept {
    if (value_ != nullptr && !close()) std::terminate();
  }

  bool serve(int streaming) {
    return open_server(&value_, streaming) && serve_one(value_, streaming);
  }

  bool close() noexcept {
    return check(coakka_http_host_service_destroy(&value_), "destroy service");
  }

 private:
  coakka_http_host_service_t *value_ = nullptr;
};
}  // namespace

int main(int argc, char **argv) {
  const int streaming = parse_mode(argc, argv);
  if (streaming < 0) return EXIT_FAILURE;
  Service service;
  const bool served = service.serve(streaming);
  return service.close() && served ? EXIT_SUCCESS : EXIT_FAILURE;
}
