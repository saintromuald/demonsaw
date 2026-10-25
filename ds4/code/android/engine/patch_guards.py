import re

p = 'android/engine/engine.cpp'
s = open(p).read()

def rep(old, new):
    global s
    assert old in s, old[:100]
    s = s.replace(old, new)

# 1) browse|add handler: guard component presence
rep("""				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (!e)
						continue;

					if (const auto c = e->get<eja::client_component>())
						m_nodes[client_key(c->get_id())] = node{ e };

					if (const auto folder = e->get<eja::folder_component>())
						m_nodes[folder_key(owner_id, folder->get_id())] = node{ e };

					if (const auto file = e->get<eja::file_component>())
						m_nodes[file_key(owner_id, file->get_id())] = node{ e };
				}""",
"""				thread_lock(incoming);
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
				}""")

# 2) search|add handler guards
rep("""				thread_lock(incoming);
				for (const auto& e : *incoming)
				{
					if (!e)
						continue;

					if (const auto folder = e->get<eja::folder_component>())
						m_nodes["sfolder:" + hex(folder->get_id())] = node{ e };

					if (const auto file = e->get<eja::file_component>())
						m_nodes["sfile:" + hex(file->get_id())] = node{ e };
				}""",
"""				thread_lock(incoming);
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
				}""")

# 3) snapshot nodes: guard child type checks
rep("""						if (const auto folder = child->get<eja::folder_component>())
						{
							std::string owner_id;""",
"""						if (child->has<eja::folder_component>())
						{
							const auto folder = child->get<eja::folder_component>();
							std::string owner_id;""")
rep("""						else if (const auto file = child->get<eja::file_component>())
						{
							std::string owner_id;""",
"""						else if (child->has<eja::file_component>())
						{
							const auto file = child->get<eja::file_component>();
							std::string owner_id;""")

# 4) search results snapshot guards (exact indentation from the file)
rep("""						if (const auto folder = e->get<eja::folder_component>())
						{
							json << "{\\"type\\":\\"folder\\"
								<< ",\\"key\\":\\"sfolder:""",
"""						if (e->has<eja::folder_component>())
						{
							const auto folder = e->get<eja::folder_component>();
							json << "{\\"type\\":\\"folder\\"
								<< ",\\"key\\":\\"sfolder:""")
rep("""						else if (const auto file = e->get<eja::file_component>())
						{
							json << "{\\"type\\":\\"file\\"
								<< ",\\"key\\":\\"sfile:""",
"""						else if (e->has<eja::file_component>())
						{
							const auto file = e->get<eja::file_component>();
							json << "{\\"type\\":\\"file\\"
								<< ",\\"key\\":\\"sfile:""")

# 5) shares snapshot guards
rep("""					if (const auto folder = e->get<eja::folder_component>())
					{
						json << "{\\"type\\":\\"folder\\"
							<< ",\\"path\\":""",
"""					if (e->has<eja::folder_component>())
					{
						const auto folder = e->get<eja::folder_component>();
						json << "{\\"type\\":\\"folder\\"
							<< ",\\"path\\":""")
rep("""					else if (const auto file = e->get<eja::file_component>())
					{
						json << "{\\"type\\":\\"file\\"
							<< ",\\"path\\":""",
"""					else if (e->has<eja::file_component>())
					{
						const auto file = e->get<eja::file_component>();
						json << "{\\"type\\":\\"file\\"
							<< ",\\"path\\":""")

# 6) share_remove guards
rep("""					for (const auto& e : *list)
					{
						std::string path;
						if (const auto folder = e->get<eja::folder_component>())
							path = folder->get_data().string();
						else if (const auto file = e->get<eja::file_component>())
							path = file->get_data().string();""",
"""					for (const auto& e : *list)
					{
						std::string path;
						if (e->has<eja::folder_component>())
							path = e->get<eja::folder_component>()->get_data().string();
						else if (e->has<eja::file_component>())
							path = e->get<eja::file_component>()->get_data().string();""")

open(p, 'w').write(s)
print('all patched')
