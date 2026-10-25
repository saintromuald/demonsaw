#include <chrono>
#include <cstdio>
#include <sstream>

#include "android/engine/engine.h"

#include "component/browse_component.h"
#include "component/callback/callback.h"
#include "component/callback/callback_service_component.h"
#include "component/chat_component.h"
#include "component/client/client_component.h"
#include "component/client/client_service_component.h"
#include "component/error_component.h"
#include "component/group/group_component.h"
#include "component/io/file_component.h"
#include "component/io/folder_component.h"
#include "component/io/share_component.h"
#include "component/mute_component.h"
#include "component/room_component.h"
#include "component/search/search_component.h"
#include "component/search/search_option_component.h"
#include "component/status/status_component.h"
#include "component/time/time_component.h"
#include "component/time/timeout_component.h"
#include "component/transfer/download_service_component.h"
#include "component/transfer/finished_component.h"
#include "component/transfer/transfer_component.h"
#include "component/transfer/upload_service_component.h"

#include "config/cli_application.h"
#include "factory/client_factory.h"
#include "object/download.h"
#include "object/upload.h"
#include "security/filter/hex.h"
#include "thread/thread_safe.h"
#include "utility/io/file_util.h"
#include "utility/io/folder_util.h"
#include "utility/value.h"

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

		// Serialize the chat list of a chat-owning entity (root, room or client).
		void chat_list_json(std::ostringstream& json, const eja::entity::ptr& entity)
		{
			json << '[';

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

			json << ']';
		}
	}

	// Hex
	std::string engine::hex(const std::string& value) { return eja::hex::encode(value); }
	std::string engine::unhex(const std::string& value) { return eja::hex::decode(value); }

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

		{
			std::lock_guard<std::mutex> lock(m_mutex);
			m_errors.clear();
			m_joined_rooms.clear();
			m_nodes.clear();
			m_search.reset();
			m_last_pump = 0;
		}

		register_callbacks();

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

	// Lookups
	eja::entity::ptr engine::find_client(const std::string& id) const
	{
		const auto entity = client();
		if (!entity)
			return nullptr;

		if (const auto map = entity->get<eja::client_map_component>())
		{
			thread_lock(map);
			const auto it = map->find(id);
			if (it != map->end())
				return it->second;
		}

		if (const auto map = entity->get<eja::entity_map_component>())
		{
			thread_lock(map);
			const auto it = map->find(id);
			if (it != map->end())
				return it->second;
		}

		return nullptr;
	}

	eja::entity::ptr engine::find_room(const std::string& id) const
	{
		const auto entity = client();
		if (!entity)
			return nullptr;

		if (const auto map = entity->get<eja::room_map_component>())
		{
			thread_lock(map);
			const auto it = map->find(id);
			if (it != map->end())
				return it->second;
		}

		return nullptr;
	}

	eja::entity::ptr engine::find_node(const std::string& key) const
	{
		std::lock_guard<std::mutex> lock(m_mutex);
		const auto it = m_nodes.find(key);
		return (it != m_nodes.end()) ? it->second.entity : nullptr;
	}

	// Callbacks
	void engine::register_callbacks()
	{
		const auto entity = client();
		if (!entity)
			return;

		const auto callback = entity->get<eja::callback_service_component>();
		if (!callback)
			return;

		// NOTE: handlers must take arguments BY VALUE (the dispatcher compares
		// std::function types and const-ref signatures never match).

		// Group chat
		callback->add(eja::callback::chat | eja::callback::add, [entity](eja::entity::ptr message)
		{
			if (const auto chats = entity->get<eja::chat_list_component>())
			{
				thread_lock(chats);
				chats->push_back(message);
			}
		});

		// Room chat: the message carries the room id
		callback->add(eja::callback::room | eja::callback::add, [entity](eja::entity::ptr message)
		{
			const auto room = message->get<eja::room_component>();
			const auto room_map = entity->get<eja::room_map_component>();

			eja::entity::ptr room_entity;
			{
				thread_lock(room_map);
				const auto it = room_map->find(room->get_id());
				if (it != room_map->end())
					room_entity = it->second;
			}

			if (room_entity)
			{
				if (const auto chats = room_entity->get<eja::chat_list_component>())
				{
					thread_lock(chats);
					chats->push_back(message);
				}
			}
		});

		// Private messages: stored on the origin client entity
		callback->add(eja::callback::pm | eja::callback::add, [](eja::entity::ptr message)
		{
			if (const auto owner = message->get_owner())
			{
				if (const auto chats = owner->get<eja::chat_list_component>())
				{
					thread_lock(chats);
					chats->push_back(message);
				}
			}
		});

		// Browse results: merge into the parent's browse list and cache ids.
		// Sharer updates arrive in a wrapper with no parent; those go into the
		// root browse list.
		callback->add(eja::callback::browse | eja::callback::add, [this, entity](eja::entity::ptr wrapper)
		{
			const auto parent = wrapper->get_parent();
			const auto incoming = wrapper->get<eja::browse_list_component>();
			if (!incoming)
				return;

			std::string owner_id;
			if (const auto sharer = wrapper->get_owner())
				if (const auto c = sharer->get<eja::client_component>())
					owner_id = c->get_id();

			{
				std::lock_guard<std::mutex> lock(m_mutex);

				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (!e)
						continue;

					if (e->has<eja::client_component>())
					{
						const auto c = e->get<eja::client_component>();
						m_nodes[client_key(c->get_id())] = node{ e };
					}
					else if (e->has<eja::folder_component>())
					{
						const auto folder = e->get<eja::folder_component>();
						m_nodes[folder_key(owner_id, folder->get_id())] = node{ e };
					}
					else if (e->has<eja::file_component>())
					{
						const auto file = e->get<eja::file_component>();
						m_nodes[file_key(owner_id, file->get_id())] = node{ e };
					}
				}
			}

			const auto list = parent
				? parent->get<eja::browse_list_component>()
				: entity->get<eja::browse_list_component>();

			if (list)
			{
				thread_lock(list);
				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (std::find(list->begin(), list->end(), e) != list->end())
						continue;

					// Sharer updates: keep only the newest entity per client
					// name (stale sessions linger on the router for minutes).
					if (e->has<eja::client_component>())
					{
						const auto c = e->get<eja::client_component>();
						for (auto it = list->begin(); it != list->end(); )
						{
							if ((*it) && (*it)->has<eja::client_component>() &&
								(*it)->get<eja::client_component>()->get_name() == c->get_name())
							{
								it = list->erase(it);
							}
							else
							{
								++it;
							}
						}
					}

					list->push_back(e);
				}
			}
		});

		// Browse clear: reset the root browse list and cached nodes
		callback->add(eja::callback::browse | eja::callback::clear, [this, entity]()
		{
			if (const auto list = entity->get<eja::browse_list_component>())
			{
				thread_lock(list);
				list->clear();
			}

			std::lock_guard<std::mutex> lock(m_mutex);
			m_nodes.clear();
		});

		// Search results: merge into the search entity's list. The wrapper has
		// no parent; the desktop matches it via the search component's id.
		callback->add(eja::callback::search | eja::callback::add, [this, entity](eja::entity::ptr wrapper)
		{
			const auto incoming = wrapper->get<eja::browse_list_component>();
			if (!incoming)
				return;

			eja::entity::ptr parent = wrapper->get_parent();
			if (!parent)
			{
				std::lock_guard<std::mutex> lock(m_mutex);
				parent = m_search;
			}
			if (!parent)
				return;

			{
				std::lock_guard<std::mutex> lock(m_mutex);
				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (!e)
						continue;

					if (e->has<eja::folder_component>())
					{
						const auto folder = e->get<eja::folder_component>();
						m_nodes["sfolder:" + hex(folder->get_id())] = node{ e };
					}
					else if (e->has<eja::file_component>())
					{
						const auto file = e->get<eja::file_component>();
						m_nodes["sfile:" + hex(file->get_id())] = node{ e };
					}
				}
			}

			if (const auto list = parent->get<eja::browse_list_component>())
			{
				thread_lock(list);
				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (std::find(list->begin(), list->end(), e) != list->end())
						continue;

					list->push_back(e);
				}
			}
		});

		// Downloads: queue and pump
		callback->add(eja::callback::download | eja::callback::add, [this, entity](eja::entity::ptr download)
		{
			const auto list = entity->get<eja::download_list_component>();
			if (!list)
				return;

			// Dedupe
			const auto file = download->get<eja::file_component>();
			const auto map = entity->get<eja::download_map_component>();
			{
				thread_lock(map);
				if (map->find(file->get_id()) != map->end())
					return;

				const auto pair = std::make_pair(file->get_id(), download);
				map->insert(pair);
			}

			{
				thread_lock(list);
				list->push_back(download);
			}

			// The pump runs on the caller thread (snapshot/command), not here:
			// download_service_component::async_start asserts thread_info::main().
		});

		// Uploads (this client serving others): start immediately
		callback->add(eja::callback::upload | eja::callback::add, [this, entity](eja::entity::ptr upload)
		{
			const auto list = entity->get<eja::upload_list_component>();
			const auto service = entity->get<eja::upload_service_component>();
			if (!list || !service)
				return;

			{
				thread_lock(list);
				list->push_back(upload);
			}

			{
				std::lock_guard<std::mutex> lock(m_mutex);
				m_pending_uploads.push_back(upload);
			}
		});

		// Finished
		callback->add(eja::callback::finished | eja::callback::add, [entity](eja::entity::ptr finished)
		{
			const auto list = entity->get<eja::finished_list_component>();
			if (!list)
				return;

			thread_lock(list);
			list->push_back(finished);
		});

		// Errors
		callback->add(eja::callback::error | eja::callback::add, [this](eja::entity::ptr error)
		{
			if (const auto e = error->get<eja::error_component>())
			{
				fprintf(stderr, "ENGINE error: %s\n", e->get_text().c_str());

				std::lock_guard<std::mutex> lock(m_mutex);
				m_errors.push_back(e->get_text());

				while (m_errors.size() > 32)
					m_errors.pop_front();
			}
		});

		// Lists are rebuilt when the group changes; drop cached browse nodes
		callback->add(eja::callback::client | eja::callback::clear, [this]()
		{
			std::lock_guard<std::mutex> lock(m_mutex);
			m_nodes.clear();
		});
	}

	// Queue pump (mirrors download_widget::on_update)
	void engine::pump()
	{
		const auto entity = client();
		if (!entity)
			return;


		const auto client = entity->get<eja::client_component>();
		const auto service = entity->get<eja::client_service_component>();
		const auto download_service = entity->get<eja::download_service_component>();
		const auto list = entity->get<eja::download_list_component>();
		if (!client || !service || !download_service || !list)
			return;

		eja::entity_list remove;
		eja::entity_list add;
		size_t active = 0;

		{
			thread_lock(list);

			for (const auto& e : *list)
			{
				const auto transfer = e->get<eja::transfer_component>();
				if (transfer->is_paused())
					continue;

				if (active < client->get_downloads())
				{
					if (transfer->is_running())
					{
						// Timeout handling
						const auto timeout = e->get<eja::timeout_component>();
						if (timeout && timeout->expired(eja::download::timeout))
						{
							download_service->async_stop(e);

							const auto transfer_list = e->get<eja::transfer_list_component>();
							bool empty = false;
							{
								thread_lock(transfer_list);
								empty = transfer_list->empty();
							}

							transfer->stop();
							transfer->set_seeds();

							if (transfer->get_retry() < eja::download::max_retry)
							{
								if (!empty && !transfer->has_progress())
									transfer->retry();

								if (client->get_downloads() < list->size())
								{
									add.push_back(e);
									remove.push_back(e);
								}
							}
							else
							{
								transfer->error();
								remove.push_back(e);
								entity->async_call(eja::callback::finished | eja::callback::add, e);
							}
						}
						else
						{
							++active;
						}
					}
					else if (!transfer->is_done())
					{
						try
						{
							const auto file = e->get<eja::file_component>();
							if (!eja::file_util::exists(file))
								eja::file_util::create(file, file->get_size());

							transfer->start();

							service->async_download(e);
							download_service->async_start(e);

							++active;
						}
						catch (const std::exception& ex)
						{
							fprintf(stderr, "ENGINE pump error: %s\n", ex.what());

							transfer->error();
							remove.push_back(e);
							entity->async_call(eja::callback::finished | eja::callback::add, e);
						}
					}
					else
					{
						// Done
						download_service->async_stop(e);
						remove.push_back(e);
						entity->async_call(eja::callback::finished | eja::callback::add, e);
					}
				}
				else
				{
					// Inactive window
					if (transfer->is_running())
					{
						download_service->async_stop(e);
						transfer->stop();
						transfer->set_seeds();

						const auto status = e->get<eja::status_component>();
						status->set_pending();
					}
					else if (transfer->is_done())
					{
						download_service->async_stop(e);
					}
				}
			}
		}

		// Start newly queued uploads (upload_service::async_start asserts main)
		{
			std::deque<eja::entity::ptr> pending;
			{
				std::lock_guard<std::mutex> lock(m_mutex);
				pending.swap(m_pending_uploads);
			}

			const auto upload_service = entity->get<eja::upload_service_component>();
			if (upload_service)
			{
				for (const auto& e : pending)
				{
					if (!e)
						continue;

					const auto transfer = e->get<eja::transfer_component>();
					if (!transfer->is_running())
					{
						transfer->start();
						upload_service->async_start(e);
					}
				}
			}
		}

		// Remove finished from the list + map
		if (!remove.empty())
		{
			const auto map = entity->get<eja::download_map_component>();

			thread_lock(list);
			for (const auto& e : remove)
			{
				const auto file = e->get<eja::file_component>();

				{
					thread_lock(map);
					map->erase(file->get_id());
				}

				const auto it = std::find(list->begin(), list->end(), e);
				if (it != list->end())
					list->erase(it);
			}
		}

		// Re-queue (rotates stalled downloads to the back)
		if (!add.empty())
		{
			thread_lock(list);
			for (const auto& e : add)
			{
				const auto it = std::find(list->begin(), list->end(), e);
				if (it != list->end())
				{
					list->erase(it);
					list->push_back(e);
				}
			}
		}

		if (!remove.empty() || !add.empty())
		{
			// Continue pumping after a short delay (allows the queue to move)
		}
	}

	// Commands
	std::string engine::command(int type, const std::string& a, const std::string& b)
	{
		const auto entity = client();
		if (!entity)
			return "{\"ok\":false}";

		const auto service = entity->get<eja::client_service_component>();
		if (!service)
			return "{\"ok\":false}";

		switch (type)
		{
			case cmd_chat:
			{
				service->async_chat(a);
				return "{\"ok\":true}";
			}
			case cmd_pm:
			{
				const auto target = find_client(unhex(a));
				if (!target)
					return "{\"ok\":false}";

				service->async_chat(target, b);
				return "{\"ok\":true}";
			}
			case cmd_room_join:
			{
				const auto room = find_room(a);
				if (!room)
					return "{\"ok\":false}";

				service->async_room_join(room);

				{
					std::lock_guard<std::mutex> lock(m_mutex);
					m_joined_rooms.insert(a);
				}

				return "{\"ok\":true}";
			}
			case cmd_room_quit:
			{
				const auto room = find_room(a);
				if (!room)
					return "{\"ok\":false}";

				service->async_room_quit(room);

				{
					std::lock_guard<std::mutex> lock(m_mutex);
					m_joined_rooms.erase(a);
				}

				return "{\"ok\":true}";
			}
			case cmd_room_chat:
			{
				service->async_chat(a, b);
				return "{\"ok\":true}";
			}
			case cmd_browse:
			{
				const auto target = find_node(a);
				if (!target)
					return "{\"ok\":false}";

				service->async_browse(target);
				return "{\"ok\":true}";
			}
			case cmd_mute:
			{
				const auto target = find_client(unhex(a));
				if (!target)
					return "{\"ok\":false}";

				const bool value = (b == "1") || (b == "true");

				const auto client = target->get<eja::client_component>();
				client->set_mute(value);

				service->async_mute(target, value);

				entity->async_call(eja::callback::client | eja::callback::update, target);
				return "{\"ok\":true}";
			}
			case cmd_share_add:
			{
				try
				{
					eja::entity::ptr share;

					if (eja::folder_util::exists(a))
					{
						const auto folder = eja::folder_component::create(a);
						share = eja::entity::create(entity, folder);
					}
					else if (eja::file_util::exists(a))
					{
						const auto file = eja::file_component::create(a);
						share = eja::entity::create(entity, file);
					}
					else
					{
						return "{\"ok\":false}";
					}

					const auto list = entity->get<eja::share_list_component>();
					{
						thread_lock(list);
						list->push_back(share);
					}

					// Full rescan: indexes the share, sets the client share flag and
					// re-announces it (the router only learns on join or ping).
					service->async_share();
					service->async_join();

					return "{\"ok\":true}";
				}
				catch (const std::exception&)
				{
					return "{\"ok\":false}";
				}
			}
			case cmd_share_remove:
			{
				const auto list = entity->get<eja::share_list_component>();
				eja::entity::ptr target;

				{
					thread_lock(list);
					for (const auto& e : *list)
					{
						std::string path;
						if (e->has<eja::folder_component>())
						{
							const auto folder = e->get<eja::folder_component>();
							path = folder->get_data().string();
						}
						else if (e->has<eja::file_component>())
						{
							const auto file = e->get<eja::file_component>();
							path = file->get_data().string();
						}

						if (path == a)
						{
							target = e;
							break;
						}
					}
				}

				if (!target)
					return "{\"ok\":false}";

				service->async_unshare();
				service->async_join();

				{
					thread_lock(list);
					const auto it = std::find(list->begin(), list->end(), target);
					if (it != list->end())
						list->erase(it);
				}

				return "{\"ok\":true}";
			}
			case cmd_download:
			{
				const auto file_entity = find_node(a);
				if (!file_entity || !file_entity->has<eja::file_component>())
					return "{\"ok\":false}";

				const auto client = entity->get<eja::client_component>();
				const auto download_entity = eja::client_factory::create_download(entity, file_entity, client->get_path());

				entity->async_call(eja::callback::download | eja::callback::add, download_entity);
				return "{\"ok\":true}";
			}
			case cmd_download_all:
			{
				const auto target = find_node(a);
				if (!target)
					return "{\"ok\":false}";

				const auto client = entity->get<eja::client_component>();
				const auto list = target->get<eja::browse_list_component>();
				if (!list)
					return "{\"ok\":false}";

				list->set_download(true);
				list->set_expanded(true);
				list->set_path(client->get_path());

				service->async_browse(target);
				return "{\"ok\":true}";
			}
			case cmd_cancel_download:
			{
				const auto download_service = entity->get<eja::download_service_component>();
				const auto list = entity->get<eja::download_list_component>();
				const auto map = entity->get<eja::download_map_component>();
				eja::entity::ptr target;

				{
					thread_lock(list);
					for (const auto& e : *list)
					{
						const auto file = e->get<eja::file_component>();
						if (file->get_id() == unhex(a))
						{
							target = e;
							break;
						}
					}
				}

				if (!target)
					return "{\"ok\":false}";

				download_service->async_stop(target);

				{
					thread_lock(list);
					const auto it = std::find(list->begin(), list->end(), target);
					if (it != list->end())
						list->erase(it);
				}

				{
					thread_lock(map);
					map->erase(unhex(a));
				}

				return "{\"ok\":true}";
			}
			case cmd_cancel_upload:
			{
				const auto upload_service = entity->get<eja::upload_service_component>();
				const auto list = entity->get<eja::upload_list_component>();
				eja::entity::ptr target;

				{
					thread_lock(list);
					for (const auto& e : *list)
					{
						const auto file = e->get<eja::file_component>();
						if (file->get_id() == unhex(a))
						{
							target = e;
							break;
						}
					}
				}

				if (!target)
					return "{\"ok\":false}";

				upload_service->async_stop(target);

				{
					thread_lock(list);
					const auto it = std::find(list->begin(), list->end(), target);
					if (it != list->end())
						list->erase(it);
				}

				return "{\"ok\":true}";
			}
			case cmd_set_download_path:
			{
				const auto client = entity->get<eja::client_component>();
				client->set_path(a);
				return "{\"ok\":true}";
			}
			case cmd_search:
			{
				const auto search_entity = eja::client_factory::create_search(entity, a);
				{
					std::lock_guard<std::mutex> lock(m_mutex);
					m_search = search_entity;
				}

				service->async_search(search_entity);

				std::ostringstream json;
				json << "{\"ok\":true,\"id\":\"" << json_escape(hex(search_entity->get<eja::search_component>()->get_id())) << "\"}";
				return json.str();
			}
			case cmd_join:
			{
				service->async_join();
				return "{\"ok\":true}";
			}
			case cmd_rooms_refresh:
			{
				service->async_room();
				return "{\"ok\":true}";
			}
			default:
			{
				return "{\"ok\":false}";
			}
		}
	}

	// State
	std::string engine::snapshot() const
	{
		// Throttle the pump: run it at most once every 500 ms.
		// NOTE: run it outside the lock; pump() takes m_mutex itself.
		{
			bool should_pump = false;
			{
				std::lock_guard<std::mutex> lock(m_mutex);
				const auto now = static_cast<size_t>(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count());
				if (now - m_last_pump >= 500)
				{
					m_last_pump = now;
					should_pump = true;
				}
			}

			if (should_pump)
				const_cast<engine*>(this)->pump();
		}

		std::ostringstream json;
		json << '{';

		const auto entity = client();
		if (!entity)
		{
			json << "\"running\":false}";
			return json.str();
		}

		std::string name;
		std::string color;
		std::string status;
		std::string self_id;

		if (const auto client = entity->get<eja::client_component>())
		{
			name = client->get_name();
			color = color_name(client->get_color());
			self_id = client->get_id();
		}

		if (const auto st = entity->get<eja::status_component>())
			status = status_name(st->get_status());

		std::string group;
		if (const auto g = entity->get<eja::group_component>())
		{
			if (g->has_id())
				group = eja::hex::encode(g->get_id());
		}

		bool sharing = false;
		if (const auto client = entity->get<eja::client_component>())
			sharing = client->is_share();

		json << "\"running\":" << (m_running ? "true" : "false")
			<< ",\"name\":\"" << json_escape(name) << '"'
			<< ",\"color\":\"" << color << '"'
			<< ",\"group\":\"" << json_escape(group) << '"'
			<< ",\"status\":\"" << status << '"'
			<< ",\"sharing\":" << (sharing ? "true" : "false")
			<< ",\"self\":\"" << json_escape(hex(self_id)) << '"';

		// Clients (with PM history and mute state)
		json << ",\"clients\":[";

		if (const auto map = entity->get<eja::client_map_component>())
		{
			bool first = true;
			{
				thread_lock(map);
				for (const auto& pair : *map)
				{
					const auto& e = pair.second;
					if (!e)
						continue;

					std::string cname;
					std::string ccolor;
					bool mute = false;

					if (const auto c = e->get<eja::client_component>())
					{
						cname = c->get_name();
						ccolor = color_name(c->get_color());
						mute = c->is_mute();
					}

					if (!first)
						json << ',';

					json << "{\"id\":\"" << json_escape(hex(pair.first)) << '"'
						<< ",\"name\":\"" << json_escape(cname) << '"'
						<< ",\"color\":\"" << ccolor << '"'
						<< ",\"mute\":" << (mute ? "true" : "false")
						<< ",\"pm\":";
					chat_list_json(json, e);
					json << '}';

					first = false;
				}
			}
		}

		json << ']';

		// Group chat
		json << ",\"chat\":";
		chat_list_json(json, entity);

		// Rooms
		json << ",\"rooms\":[";

		if (const auto map = entity->get<eja::room_map_component>())
		{
			bool first = true;
			{
				thread_lock(map);
				for (const auto& pair : *map)
				{
					const auto& room = pair.second;
					if (!room)
						continue;

					const auto r = room->get<eja::room_component>();

					if (!first)
						json << ',';

					json << "{\"id\":\"" << json_escape(r->get_id()) << '"'
						<< ",\"name\":\"" << json_escape(r->get_name()) << '"'
						<< ",\"color\":\"" << color_name(r->get_color()) << '"'
						<< ",\"size\":" << r->get_size()
						<< ",\"joined\":";

					bool joined = false;
					{
						std::lock_guard<std::mutex> lock(m_mutex);
						joined = (m_joined_rooms.find(r->get_id()) != m_joined_rooms.end());
					}

					json << (joined ? "true" : "false")
						<< ",\"chat\":";
					chat_list_json(json, room);
					json << '}';

					first = false;
				}
			}
		}

		json << ']';

		// Browse tree: top-level sharers
		json << ",\"browse\":[";

		if (const auto list = entity->get<eja::browse_list_component>())
		{
			bool first = true;
			{
				thread_lock(list);
				for (const auto& e : *list)
				{
					if (!e)
						continue;

					std::string cid;
					std::string cname;
					std::string ccolor;
					u64 size = 0;

					if (const auto c = e->get<eja::client_component>())
					{
						cid = c->get_id();
						cname = c->get_name();
						ccolor = color_name(c->get_color());
						size = c->get_size();
					}

					if (!first)
						json << ',';

					json << "{\"key\":\"" << json_escape(client_key(cid)) << '"'
						<< ",\"name\":\"" << json_escape(cname) << '"'
						<< ",\"color\":\"" << ccolor << '"'
						<< ",\"size\":" << size << '}';

					first = false;
				}
			}
		}

		json << ']';

		// Browsed nodes (children of entities we fetched)
		json << ",\"nodes\":{";

		{
			bool first = true;
			std::lock_guard<std::mutex> lock(m_mutex);

			for (const auto& pair : m_nodes)
			{
				const auto& key = pair.first;
				const auto& e = pair.second.entity;
				if (!e)
					continue;

				// Only folders and client entities have children
				if (!e->has<eja::browse_list_component>())
					continue;

				const auto list = e->get<eja::browse_list_component>();

				if (!first)
					json << ',';

				json << '"' << json_escape(key) << "\":{\"children\":[";

				{
					bool first_child = true;
					thread_lock(list);
					for (const auto& child : *list)
					{
						if (!child)
							continue;

						if (!first_child)
							json << ',';

						if (child->has<eja::folder_component>())
						{
							const auto folder = child->get<eja::folder_component>();
							std::string owner_id;
							if (const auto owner = child->get_owner())
								if (const auto c = owner->get<eja::client_component>())
									owner_id = c->get_id();

							json << "{\"type\":\"folder\""
								<< ",\"key\":\"" << json_escape(folder_key(owner_id, folder->get_id())) << '"'
								<< ",\"name\":\"" << json_escape(folder->get_name()) << '"'
								<< ",\"size\":" << folder->get_size()
								<< ",\"time\":" << folder->get_time()
								<< ",\"drive\":" << (folder->is_drive() ? "true" : "false") << '}';
						}
						else if (child->has<eja::file_component>())
						{
							const auto file = child->get<eja::file_component>();
							std::string owner_id;
							if (const auto owner = child->get_owner())
								if (const auto c = owner->get<eja::client_component>())
									owner_id = c->get_id();

							json << "{\"type\":\"file\""
								<< ",\"key\":\"" << json_escape(file_key(owner_id, file->get_id())) << '"'
								<< ",\"id\":\"" << json_escape(hex(file->get_id())) << '"'
								<< ",\"name\":\"" << json_escape(file->get_name()) << '"'
								<< ",\"size\":" << file->get_size()
								<< ",\"time\":" << file->get_time()
								<< ",\"ext\":\"" << json_escape(file->get_extension()) << "\"}";
						}

						first_child = false;
					}
				}

				json << "]}";

				first = false;
			}
		}

		json << '}';

		// Shares
		json << ",\"shares\":[";

		if (const auto list = entity->get<eja::share_list_component>())
		{
			bool first = true;
			{
				thread_lock(list);
				for (const auto& e : *list)
				{
					if (!e)
						continue;

					if (!first)
						json << ',';

					if (e->has<eja::folder_component>())
					{
						const auto folder = e->get<eja::folder_component>();
						json << "{\"type\":\"folder\""
							<< ",\"path\":\"" << json_escape(folder->get_data().string()) << '"'
							<< ",\"name\":\"" << json_escape(folder->get_name()) << '"'
							<< ",\"size\":" << folder->get_size() << '}';
					}
					else if (e->has<eja::file_component>())
					{
						const auto file = e->get<eja::file_component>();
						json << "{\"type\":\"file\""
							<< ",\"path\":\"" << json_escape(file->get_data().string()) << '"'
							<< ",\"name\":\"" << json_escape(file->get_name()) << '"'
							<< ",\"size\":" << file->get_size() << '}';
					}

					first = false;
				}
			}
		}

		json << ']';

		// Transfers
		auto transfer_json = [&](std::ostringstream& out, const eja::entity::ptr& e)
		{
			const auto transfer = e->get<eja::transfer_component>();
			const auto file = e->get<eja::file_component>();
			const auto st = e->get<eja::status_component>();

			out << "{\"id\":\"" << json_escape(hex(file->get_id())) << '"'
				<< ",\"name\":\"" << json_escape(file->get_name()) << '"'
				<< ",\"size\":" << file->get_size()
				<< ",\"percent\":" << transfer->get_percent()
				<< ",\"speed\":" << transfer->get_speed()
				<< ",\"eta\":\"" << json_escape(transfer->get_time()) << '"'
				<< ",\"status\":\"" << status_name(st->get_status()) << '"'
				<< ",\"done\":" << (transfer->is_done() ? "true" : "false")
				<< ",\"errored\":" << (transfer->is_errored() ? "true" : "false")
				<< ",\"paused\":" << (transfer->is_paused() ? "true" : "false")
				<< ",\"running\":" << (transfer->is_running() ? "true" : "false") << '}';
		};

		json << ",\"downloads\":[";

		if (const auto list = entity->get<eja::download_list_component>())
		{
			bool first = true;
			{
				thread_lock(list);
				for (const auto& e : *list)
				{
					if (!e)
						continue;

					if (!first)
						json << ',';

					transfer_json(json, e);
					first = false;
				}
			}
		}

		json << "],\"uploads\":[";

		if (const auto list = entity->get<eja::upload_list_component>())
		{
			bool first = true;
			{
				thread_lock(list);
				for (const auto& e : *list)
				{
					if (!e)
						continue;

					if (!first)
						json << ',';

					transfer_json(json, e);
					first = false;
				}
			}
		}

		json << "],\"finished\":[";

		if (const auto list = entity->get<eja::finished_list_component>())
		{
			bool first = true;
			{
				thread_lock(list);
				for (const auto& e : *list)
				{
					if (!e)
						continue;

					const auto file = e->get<eja::file_component>();
					const auto transfer = e->get<eja::transfer_component>();
					const auto st = e->get<eja::status_component>();

					if (!first)
						json << ',';

					json << "{\"name\":\"" << json_escape(file->get_name()) << '"'
						<< ",\"size\":" << file->get_size()
						<< ",\"status\":\"" << status_name(st->get_status()) << '"'
						<< ",\"done\":" << (transfer->is_done() ? "true" : "false")
						<< ",\"errored\":" << (transfer->is_errored() ? "true" : "false") << '}';

					first = false;
				}
			}
		}

		json << ']';

		// Search results
		json << ",\"search\":{";

		{
			std::lock_guard<std::mutex> lock(m_mutex);
			const auto search = m_search;
			std::string text;

			if (search)
			{
				if (const auto option = search->get<eja::search_option_component>())
					text = option->get_text();

				json << "\"text\":\"" << json_escape(text) << "\",\"results\":[";

				if (const auto list = search->get<eja::browse_list_component>())
				{
					bool first = true;
					thread_lock(list);
					for (const auto& e : *list)
					{
						if (!e)
							continue;

						if (!first)
							json << ',';

						if (e->has<eja::folder_component>())
						{
							const auto folder = e->get<eja::folder_component>();
							json << "{\"type\":\"folder\""
								<< ",\"key\":\"sfolder:" << json_escape(hex(folder->get_id())) << '"'
								<< ",\"name\":\"" << json_escape(folder->get_name()) << '"'
								<< ",\"size\":" << folder->get_size() << '}';
						}
						else if (e->has<eja::file_component>())
						{
							const auto file = e->get<eja::file_component>();
							json << "{\"type\":\"file\""
								<< ",\"key\":\"sfile:" << json_escape(hex(file->get_id())) << '"'
								<< ",\"name\":\"" << json_escape(file->get_name()) << '"'
								<< ",\"size\":" << file->get_size() << '}';
						}

						first = false;
					}
				}

				json << ']';
			}
			else
			{
				json << "\"text\":\"\",\"results\":[]";
			}
		}

		json << '}';

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

		json << ']';

		// Download path
		{
			std::string path;
			if (const auto client = entity->get<eja::client_component>())
				path = client->get_path().string();

			json << ",\"download_path\":\"" << json_escape(path) << '"';
		}

		json << '}';

		return json.str();
	}
}
