#include "stream_lane_camera_lane_owner.h"

#include <array>
#include <cstddef>

namespace {

struct FakeLane {
  std::array<int, 2> calls{};
  std::size_t call_count = 0u;
};

struct FakeApi {
  using Lane = FakeLane;

  static int stop(Lane *lane) noexcept {
    lane->calls.at(lane->call_count++) = 1;
    return 0;
  }

  static void destroy(Lane *lane) noexcept {
    lane->calls.at(lane->call_count++) = 2;
  }
};

} // namespace

int main() {
  FakeLane lane;
  {
    coakka::v2::camera::BasicLaneOwner<FakeApi> owner(&lane);
    if (owner.get() != &lane) {
      return 1;
    }
  }
  if (lane.call_count != 2u || lane.calls[0] != 1 || lane.calls[1] != 2) {
    return 2;
  }

  FakeLane reset_lane;
  {
    coakka::v2::camera::BasicLaneOwner<FakeApi> owner(&reset_lane);
    owner.reset();
    owner.reset();
  }
  if (reset_lane.call_count != 2u || reset_lane.calls[0] != 1 ||
      reset_lane.calls[1] != 2) {
    return 3;
  }

  return 0;
}
