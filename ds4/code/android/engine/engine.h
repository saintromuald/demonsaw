#ifndef _DEMONSAW_ENGINE_H_
#define _DEMONSAW_ENGINE_H_

#include <deque>
#include <memory>
#include <mutex>
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
	// All methods must be called from the same thread. The engine owns the
	// client entity graph and exposes a JSON snapshot that the UI polls.
	class engine final
	{
	public:
		engine();
		~engine();

		// Lifecycle
		bool start(const std::string& config_path);
		void stop();
		bool running() const { return m_running; }

		// Commands
		bool chat(const std::string& text);
		bool join();
		bool browse();

		// State
		std::string snapshot() const;

	private:
		eja::entity::ptr client() const;

	private:
		int m_argc = 1;
		char* m_argv[2];
		std::unique_ptr<eja::cli_application> m_app;
		mutable std::mutex m_mutex;
		std::deque<std::string> m_errors;
		bool m_running = false;
	};
}

#endif
