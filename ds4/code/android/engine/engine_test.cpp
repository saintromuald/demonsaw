#include <chrono>
#include <iostream>
#include <string>
#include <thread>

#include "android/engine/engine.h"

// Headless smoke test for one client.
//
//   engine_test <client.toml>                watch: print a snapshot every second
//   engine_test <client.toml> "some text"    wait for join, then send a chat
//
// The download throttle is a process-wide singleton in Demonsaw, so two
// clients cannot live in one process; run two instances for a chat test.
int main(int argc, char* argv[])
{
	if (argc < 2)
	{
		std::cerr << "usage: " << argv[0] << " <client.toml> [message]" << std::endl;
		return 2;
	}

	demonsaw::engine engine;
	if (!engine.start(argv[1]))
	{
		std::cerr << "engine failed to start" << std::endl;
		return 1;
	}

	const auto is_success = [](const std::string& snapshot)
	{
		return snapshot.find("\"status\":\"success\"") != std::string::npos;
	};

	for (int i = 0; i < 40 && !is_success(engine.snapshot()); ++i)
		std::this_thread::sleep_for(std::chrono::milliseconds(250));

	engine.browse();
	std::this_thread::sleep_for(std::chrono::seconds(1));

	if (argc >= 3)
	{
		std::cout << "before send: " << engine.snapshot() << std::endl;

		for (int i = 0; i < 3; ++i)
		{
			engine.chat(argv[2]);
			std::this_thread::sleep_for(std::chrono::seconds(1));
		}

		std::cout << "after send: " << engine.snapshot() << std::endl;
	}
	else
	{
		for (int i = 0; i < 60; ++i)
		{
			std::cout << engine.snapshot() << std::endl;
			std::this_thread::sleep_for(std::chrono::seconds(1));
		}
	}

	engine.stop();
	return 0;
}
