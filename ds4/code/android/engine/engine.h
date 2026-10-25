#ifndef _DEMONSAW_ENGINE_H_
#define _DEMONSAW_ENGINE_H_

#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>

#include "entity/entity.h"

namespace eja
{
	class cli_application;
}

namespace demonsaw
{
	// Headless Demonsaw client used by the Android front end.
	//
	// All public methods must be called from the same thread. The engine owns
	// the client entity graph, mirrors the desktop UI's model/queue logic and
	// exposes a JSON snapshot that the UI polls.
	class engine final
	{
	public:
		// Commands (see org.demonsaw.app.Native for the mapping)
		enum command_type
		{
			cmd_chat = 1,             // a = text
			cmd_pm = 2,               // a = client id (hex), b = text
			cmd_room_join = 3,        // a = room id
			cmd_room_quit = 4,        // a = room id
			cmd_room_chat = 5,        // a = room id, b = text
			cmd_browse = 6,           // a = node key
			cmd_mute = 7,             // a = client id (hex), b = "1"/"0"
			cmd_share_add = 8,        // a = filesystem path
			cmd_share_remove = 9,     // a = filesystem path
			cmd_download = 10,        // a = file node key
			cmd_cancel_download = 11, // a = file id (hex)
			cmd_cancel_upload = 12,   // a = file id (hex)
			cmd_set_download_path = 13, // a = path
			cmd_search = 14,          // a = text
			cmd_download_all = 15,    // a = folder/client node key
			cmd_join = 16,            // re-join the group
			cmd_rooms_refresh = 17,   // refresh client/room/sharer lists
		};

	public:
		engine();
		~engine();

		// Lifecycle
		bool start(const std::string& config_path);
		void stop();
		bool running() const { return m_running; }

		// Commands
		std::string command(int type, const std::string& a, const std::string& b);

		// State
		std::string snapshot() const;

	private:
		struct node
		{
			eja::entity::ptr entity;
		};

	private:
		eja::entity::ptr client() const;

		void register_callbacks();
		void pump();

		eja::entity::ptr find_client(const std::string& id) const;      // raw id
		eja::entity::ptr find_room(const std::string& id) const;
		eja::entity::ptr find_node(const std::string& key) const;

		static std::string client_key(const std::string& id) { return "client:" + hex(id); }
		static std::string file_key(const std::string& owner_id, const std::string& file_id) { return "file:" + hex(owner_id) + ":" + hex(file_id); }
		static std::string folder_key(const std::string& owner_id, const std::string& folder_id) { return "folder:" + hex(owner_id) + ":" + hex(folder_id); }

		static std::string hex(const std::string& value);
		static std::string unhex(const std::string& value);

	private:
		int m_argc = 1;
		char* m_argv[2];
		std::unique_ptr<eja::cli_application> m_app;
		bool m_running = false;

		mutable std::mutex m_mutex;
		std::deque<std::string> m_errors;
		std::deque<eja::entity::ptr> m_pending_uploads;
		std::set<std::string> m_joined_rooms;
		std::map<std::string, node> m_nodes;
		eja::entity::ptr m_search;

		mutable size_t m_last_pump = 0;
	};
}

#endif
