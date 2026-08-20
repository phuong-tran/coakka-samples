#pragma once

#include <utility>

#include "coakka/v2/stream_lane.h"

namespace coakka::v2::camera {

struct StreamLaneApi {
  using Lane = coakka_v2_stream_lane_t;

  static coakka_v2_status_t stop(Lane *lane) noexcept {
    return coakka_v2_stream_lane_stop(lane);
  }

  static void destroy(Lane *lane) noexcept {
    coakka_v2_stream_lane_destroy(lane);
  }
};

template <typename Api> class BasicLaneOwner final {
public:
  using Lane = typename Api::Lane;

  explicit BasicLaneOwner(Lane *lane = nullptr) noexcept : value_(lane) {}

  BasicLaneOwner(const BasicLaneOwner &) = delete;
  BasicLaneOwner &operator=(const BasicLaneOwner &) = delete;
  BasicLaneOwner(BasicLaneOwner &&) = delete;
  BasicLaneOwner &operator=(BasicLaneOwner &&) = delete;

  ~BasicLaneOwner() noexcept { reset(); }

  [[nodiscard]] Lane *get() const noexcept { return value_; }

  void reset() noexcept {
    Lane *owned = std::exchange(value_, nullptr);
    if (owned == nullptr) {
      return;
    }
    (void)Api::stop(owned);
    Api::destroy(owned);
  }

private:
  Lane *value_;
};

using StreamLaneOwner = BasicLaneOwner<StreamLaneApi>;

} // namespace coakka::v2::camera
