#include <cstdio>
#include <sstream>
#include <string>

#include "android/engine/engine.h"

#include "component/callback/callback.h"
#include "component/callback/callback_service_component.h"
#include "component/chat_component.h"
#include "component/error_component.h"
#include "component/client/client_component.h"
#include "component/client/client_service_component.h"
#include "component/group/group_component.h"
#include "component/status/status_component.h"
#include "component/time/time_component.h"

#include "config/cli_application.h"
#include "entity/entity.h"
#include "security/filter/hex.h"
#include "thread/thread_safe.h"

namespace demonsaw
{
	namespace
	{
		const char* status_name(const eja::status value)
		{
			switch (value)
			{
				case eja::status::pending: return "pending";
				case eja::status::info: return "info";
				case eja::status::success: return "success";
				case eja::status::warning: return "warning";
				case eja::status::error: return "error";
				case eja::status::cancelled: return "cancelled";
				default: return "none";
			}
		}

		std::string json_escape(const std::string& text)
		{
			std::string out;
			out.reserve(text.size() + 8);

			for (const unsigned char c : text)
			{
				switch (c)
				{
					case '"': out += "\\\""; break;
					case '\\': out += "\\\\"; break;
					case '\b': out += "\\b"; break;
					case '\f': out += "\\f"; break;
					case '\n': out += "\\n"; break;
					case '\r': out += "\\r"; break;
					case '\t': out += "\\t"; break;
					default:
						if (c < 0x20)
						{
							char buffer[7];
							std::snprintf(buffer, sizeof(buffer), "\\u%04x", c);
							out += buffer;
						}
						else
						{
							out += static_cast<char>(c);
						}
						break;
				}
			}

			return out;
		}

		std::string color_name(const u32 color)
		{
			char buffer[7];
			std::snprintf(buffer, sizeof(buffer), "%06x", color & 0xFFFFFF);
			return buffer;
		}
	}

	engine::engine()
	{
		m_argv[0] = const_cast<char*>("demonsaw");
		m_argv[1] = nullptr;
	}

	engine::~engine()
	{
		stop();
	}

	// Lifecycle
	bool engine::start(const std::string& config_path)
	{
		if (m_running)
			stop();

		m_app.reset(new eja::cli_application(m_argc, m_argv));
		m_app->set_path(config_path);

		try
		{
			m_app->read();
		}
		catch (const std::exception&)
		{
			m_app.reset();
			return false;
		}

		if (!m_app->has_clients())
		{
			m_app.reset();
			return false;
		}

		// Capture errors for the UI.
		{
			std::lock_guard<std::mutex> lock(m_mutex);
			m_errors.clear();
		}

		if (const auto entity = client())
		{
			if (const auto callback = entity->get<eja::callback_service_component>())
			{
				// The desktop app's chat model appends incoming messages to the
				// chat list; a headless client has to do it itself.
				callback->add(eja::callback::chat | eja::callback::add, [entity](eja::entity::ptr message)
				{
					if (const auto chats = entity->get<eja::chat_list_component>())
					{
						thread_lock(chats);
						chats->push_back(message);
					}
				});
				callback->add(eja::callback::error | eja::callback::add, [this](eja::entity::ptr error)
				{
					if (const auto e = error->get<eja::error_component>())
					{
						std::lock_guard<std::mutex> lock(m_mutex);
						m_errors.push_back(e->get_text());

						while (m_errors.size() > 32)
							m_errors.pop_front();
					}
				});
			}
		}

		m_app->init();
		m_running = true;

		return true;
	}

	void engine::stop()
	{
		if (!m_app)
			return;

		if (m_running)
			m_app->shutdown();

		m_app->clear();
		m_app.reset();
		m_running = false;
	}

	// Entity
	eja::entity::ptr engine::client() const
	{
		if (!m_app || !m_app->has_clients())
			return nullptr;

		const auto& clients = m_app->get_clients();
		return clients.empty() ? nullptr : clients.front();
	}

	// Commands
	bool engine::chat(const std::string& text)
	{
		const auto entity = client();
		if (!entity)
			return false;

		const auto service = entity->get<eja::client_service_component>();
		if (!service)
			return false;

		service->async_chat(text);
		return true;
	}

	bool engine::join()
	{
		const auto entity = client();
		if (!entity)
			return false;

		const auto service = entity->get<eja::client_service_component>();
		if (!service)
			return false;

		service->async_join();
		return true;
	}

	bool engine::browse()
	{
		const auto entity = client();
		if (!entity)
			return false;

		const auto service = entity->get<eja::client_service_component>();
		if (!service)
			return false;

		service->async_browse(entity);
		return true;
	}

	// State
	std::string engine::snapshot() const
	{
		std::ostringstream json;

		json << '{';

		const auto entity = client();
		if (!entity)
		{
			json << "\"running\":false}";
			return json.str();
		}

		// Client
		std::string name;
		std::string color;
		std::string status;

		if (const auto client = entity->get<eja::client_component>())
		{
			name = client->get_name();
			color = color_name(client->get_color());
		}

		if (const auto st = entity->get<eja::status_component>())
			status = status_name(st->get_status());

		// Group
		std::string group;
		if (const auto g = entity->get<eja::group_component>())
		{
			if (g->has_id())
				group = eja::hex::encode(g->get_id());
		}

		json << "\"running\":" << (m_running ? "true" : "false")
			<< ",\"name\":\"" << json_escape(name) << '"'
			<< ",\"color\":\"" << color << '"'
			<< ",\"group\":\"" << json_escape(group) << '"'
			<< ",\"status\":\"" << status << '"';

		// Clients
		json << ",\"clients\":[";

		if (const auto clients = entity->get<eja::client_list_component>())
		{
			thread_lock(clients);

			bool first = true;
			for (const auto& e : *clients)
			{
				if (!e)
					continue;

				if (!first)
					json << ',';

				std::string cname;
				std::string ccolor;
				std::string cstatus;

				if (const auto c = e->get<eja::client_component>())
				{
					cname = c->get_name();
					ccolor = color_name(c->get_color());
				}

				if (const auto s = e->get<eja::status_component>())
					cstatus = status_name(s->get_status());

				json << "{\"name\":\"" << json_escape(cname) << '"'
					<< ",\"color\":\"" << ccolor << '"'
					<< ",\"status\":\"" << cstatus << "\"}";

				first = false;
			}
		}

		json << ']';

		// Chat
		json << ",\"chat\":[";

		if (const auto chats = entity->get<eja::chat_list_component>())
		{
			thread_lock(chats);

			bool first = true;
			for (const auto& e : *chats)
			{
				if (!e)
					continue;

				std::string text;
				if (const auto chat = e->get<eja::chat_component>())
					text = chat->get_text();

				std::string owner_name;
				std::string owner_color;

				if (const auto owner = e->get_owner())
				{
					if (const auto c = owner->get<eja::client_component>())
					{
						owner_name = c->get_name();
						owner_color = color_name(c->get_color());
					}
				}

				long long stamp = 0;
				if (const auto t = e->get<eja::time_component>())
					stamp = static_cast<long long>(t->get_time());

				if (!first)
					json << ',';

				json << "{\"name\":\"" << json_escape(owner_name) << '"'
					<< ",\"color\":\"" << owner_color << '"'
					<< ",\"text\":\"" << json_escape(text) << '"'
					<< ",\"time\":" << stamp << '}';

				first = false;
			}
		}

		json << "]";

		// Errors
		json << ",\"errors\":[";

		{
			std::lock_guard<std::mutex> lock(m_mutex);

			bool first = true;
			for (const auto& error : m_errors)
			{
				if (!first)
					json << ',';

				json << '"' << json_escape(error) << '"';
				first = false;
			}
		}

		json << "]}";

		return json.str();
	}
}
