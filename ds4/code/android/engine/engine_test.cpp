#include <chrono>
#include <iostream>
#include <string>
#include <thread>

#include "android/engine/engine.h"

// Headless smoke-test client.
//
//   engine_test <cfg>                       watch: print snapshots every second
//   engine_test <cfg> "<text>"              send a group chat, then exit
//   engine_test <cfg> roomchat <id> <text>  join room, send, then watch 45s
//   engine_test <cfg> roomjoin <id>         join room, then watch 45s
//   engine_test <cfg> pm <clientid> <text>  send a private message, watch 45s
//   engine_test <cfg> share <path>          share a folder/file, watch 45s
int main(int argc, char* argv[])
{
	if (argc < 2)
	{
		std::cerr << "usage: " << argv[0] << " <client.toml> [message | roomchat <id> <text> | roomjoin <id> | pm <clientid> <text> | share <path>]" << std::endl;
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

	const std::string mode = (argc >= 3) ? argv[2] : "";

	if (mode == "roomchat" && argc >= 5)
	{
		engine.command(demonsaw::engine::cmd_room_join, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(2));
		engine.command(demonsaw::engine::cmd_room_chat, argv[3], argv[4]);
	}
	else if (mode == "roomjoin" && argc >= 4)
	{
		engine.command(demonsaw::engine::cmd_room_join, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(2));
	}
	else if (mode == "pm" && argc >= 5)
	{
		engine.command(demonsaw::engine::cmd_pm, argv[3], argv[4]);
		std::this_thread::sleep_for(std::chrono::seconds(2));
	}
	else if (mode == "share" && argc >= 4)
	{
		engine.command(demonsaw::engine::cmd_share_add, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(3));
	}
	else if (mode == "browse" && argc >= 4)
	{
		engine.command(demonsaw::engine::cmd_browse, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(2));
	}
	else if (mode == "browsefull")
	{
		// One process: browse the first sharer, then browse into its first folder.
		std::string key;
		for (int i = 0; i < 30 && key.empty(); ++i)
		{
			const auto snap = engine.snapshot();
			const auto pos = snap.find("\"browse\":[{");
			if (pos != std::string::npos)
			{
				const auto kp = snap.find("\"key\":\"client:", pos);
				if (kp != std::string::npos)
				{
					const auto start = kp + 7;
					const auto end = snap.find('"', start);
					if (end != std::string::npos)
						key = snap.substr(start, end - start);
				}
			}
			std::this_thread::sleep_for(std::chrono::seconds(1));
		}

		if (key.empty())
		{
			std::cerr << "no sharer" << std::endl;
		}
		else
		{
			engine.command(demonsaw::engine::cmd_browse, key, "");
			std::this_thread::sleep_for(std::chrono::seconds(3));

			std::string fkey;
			const auto snap = engine.snapshot();
			const auto fpos = snap.find("\"key\":\"folder:");
			if (fpos != std::string::npos)
			{
				const auto start = fpos + 7;
				const auto end = snap.find('"', start);
				if (end != std::string::npos)
					fkey = snap.substr(start, end - start);
			}

			if (fkey.empty())
			{
				std::cerr << "no folder found in browse" << std::endl;
			}
			else
			{
				std::cerr << "browsing folder " << fkey << std::endl;
				engine.command(demonsaw::engine::cmd_browse, fkey, "");
			}
		}
	}
	else if (mode == "download")
	{
		engine.command(demonsaw::engine::cmd_set_download_path, "/tmp/opencode/dlout", "");

		std::string key;
		for (int i = 0; i < 30 && key.empty(); ++i)
		{
			const auto snap = engine.snapshot();
			const auto pos = snap.find("\"browse\":[{");
			if (pos != std::string::npos)
			{
				const auto kp = snap.find("\"key\":\"client:", pos);
				if (kp != std::string::npos)
				{
					const auto start = kp + 7;
					const auto end = snap.find('"', start);
					if (end != std::string::npos)
						key = snap.substr(start, end - start);
				}
			}
			std::this_thread::sleep_for(std::chrono::seconds(1));
		}

		if (!key.empty())
			engine.command(demonsaw::engine::cmd_browse, key, "");
		std::this_thread::sleep_for(std::chrono::seconds(3));

		// Browse into the first folder
		{
			const auto snap = engine.snapshot();
			const auto fpos = snap.find("\"key\":\"folder:");
			if (fpos != std::string::npos)
			{
				const auto start = fpos + 7;
				const auto end = snap.find('"', start);
				if (end != std::string::npos)
				{
					const auto fkey = snap.substr(start, end - start);
					engine.command(demonsaw::engine::cmd_browse, fkey, "");
				}
			}
		}
		std::this_thread::sleep_for(std::chrono::seconds(3));

		// Extract the first file key from the folder's children
		std::string file_key;
		{
			const auto snap = engine.snapshot();
			const auto fpos = snap.find("\"type\":\"file\"");
			if (fpos != std::string::npos)
			{
				const auto kp = snap.find("\"key\":\"file:", fpos);
				if (kp != std::string::npos)
				{
					const auto start = kp + 7;
					const auto end = snap.find('"', start);
					if (end != std::string::npos)
						file_key = snap.substr(start, end - start);
				}
			}
		}

		if (file_key.empty())
		{
			std::cerr << "no file to download" << std::endl;
		}
		else
		{
			std::cerr << "downloading " << file_key << std::endl;
			engine.command(demonsaw::engine::cmd_download, file_key, "");
		}
	}
	else if (mode == "search" && argc >= 4)
	{
		engine.command(demonsaw::engine::cmd_search, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(2));
	}
	else if (mode == "browsefolder" && argc >= 4)
	{
		engine.command(demonsaw::engine::cmd_browse, argv[3], "");
		std::this_thread::sleep_for(std::chrono::seconds(2));
	}
	else if (mode == "browsesharer")
	{
		// Wait for a sharer to appear, then browse it (fresh client id).
		std::string key;
		for (int i = 0; i < 30 && key.empty(); ++i)
		{
			const auto snap = engine.snapshot();
			const auto pos = snap.find("\"browse\":[{");
			if (pos != std::string::npos)
			{
				const auto kp = snap.find("\"key\":\"client:", pos);
				if (kp != std::string::npos)
				{
					const auto start = kp + 7;
					const auto end = snap.find('"', start);
					if (end != std::string::npos)
						key = snap.substr(start, end - start);
				}
			}
			std::this_thread::sleep_for(std::chrono::seconds(1));
		}

		if (!key.empty())
		{
			std::cerr << "browsing " << key << std::endl;
			engine.command(demonsaw::engine::cmd_browse, key, "");
		}
		else
		{
			std::cerr << "no sharer found" << std::endl;
		}
	}
	else if (argc >= 3)
	{
		engine.command(demonsaw::engine::cmd_chat, argv[2], "");
		std::this_thread::sleep_for(std::chrono::seconds(1));
	}

	for (int i = 0; i < 300; ++i)
	{
		std::cout << engine.snapshot() << std::endl;
		std::this_thread::sleep_for(std::chrono::seconds(1));
	}

	engine.stop();
	return 0;
}
