/** C++ lifetime management over the focused public C host SDK. */
#include "../native/host-outbound/example.h"

#include <cstdlib>
#include <exception>

namespace {
class Service final {
 public:
  Service() = default;
  Service(const Service &) = delete;
  Service &operator=(const Service &) = delete;

  ~Service() noexcept {
    if (!close()) std::terminate();
  }

  bool open_upstream() { return sample_open_upstream(&value_); }
  bool open_client(const Service &upstream) {
    return sample_open_client(upstream.value_, &value_);
  }
  bool close() noexcept {
    // Failure preserves the owner. The destructor can retry an explicit close.
    return sample_check(coakka_http_host_service_destroy(&value_), "destroy service");
  }
  coakka_http_host_service_t *get() const noexcept { return value_; }

 private:
  coakka_http_host_service_t *value_ = nullptr;
};
}  // namespace

int main() {
  Service upstream;
  Service client;
  const bool ran = upstream.open_upstream() && client.open_client(upstream) &&
                   sample_run(upstream.get(), client.get());
  if (!client.close() || !upstream.close() || !ran) return EXIT_FAILURE;
  puts("host-outbound-smoke=pass");
  return EXIT_SUCCESS;
}
