#include "coakka/logger/native_cpp/Connector.h"

#include <chrono>
#include <iostream>
#include <stdexcept>

int main() {
  using coakka::logger::native_cpp::LoggerOrchestrator;
  using coakka::logger::native_cpp::StartSpec;

  const auto info = LoggerOrchestrator::readLoggerInfo();
  std::cout << "coakka_logger_info abi=" << info.abi_version
            << " version=" << info.runtime_version
            << " git=" << info.git_commit
            << " language=cpp\n";

  StartSpec spec;
  spec.system_name = "native-cpp-basic-logger";
  spec.queue_capacity = 8;
  spec.category_capacity = 96;
  spec.message_capacity = 160;

  LoggerOrchestrator logger(spec);
  const auto sequence =
      logger.logInfo("samples.logger.native.cpp.basic", "{\"event\":\"hello\",\"language\":\"cpp\"}");
  const auto record = logger.await(std::chrono::milliseconds(100));
  if (!record.has_value()) {
    throw std::runtime_error("expected one logger record");
  }

  std::cout << "coakka_logger_record sequence=" << record->sequence
            << " level=" << record->level_name
            << " category=" << record->category
            << " message=" << record->message
            << '\n';

  const auto stats = logger.readStats();
  std::cout << "coakka_logger_stats emitted=" << stats.emitted_count
            << " delivered=" << stats.delivered_count
            << " dropped=" << stats.dropped_count
            << " language=cpp\n";

  logger.stop();
  return sequence == 1 ? 0 : 1;
}
