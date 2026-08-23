import { useState, useEffect } from 'react';
import { fetchCurrentUser } from './lib/api';
import type { User, Project } from './lib/api';
import { ProjectDashboard } from './features/projects/ProjectDashboard';
import { ProjectDetail } from './features/projects/ProjectDetail';
import { 
  Boxes, 
  LogOut, 
  GitBranch
} from 'lucide-react';

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [loadingUser, setLoadingUser] = useState(true);

  useEffect(() => {
    fetchCurrentUser()
      .then((data) => setUser(data))
      .catch((e) => console.error(e))
      .finally(() => setLoadingUser(false));
  }, []);

  const handleLogin = () => {
    window.location.href = '/oauth2/authorization/github';
  };

  const handleLogout = () => {
    window.location.href = '/logout';
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-500 selection:text-white">
      {/* Navigation Header */}
      <header className="sticky top-0 z-40 bg-slate-950/80 backdrop-blur-xl border-b border-slate-800/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div 
            onClick={() => setSelectedProject(null)}
            className="flex items-center space-x-3 cursor-pointer group"
          >
            <div className="p-2 rounded-xl bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 group-hover:scale-105 transition">
              <Boxes className="w-5 h-5" />
            </div>
            <div className="flex items-baseline space-x-2">
              <span className="font-extrabold text-xl tracking-tight text-white">RivetDeploy</span>
              <span className="text-[10px] uppercase font-bold tracking-widest text-indigo-400 bg-indigo-950/60 px-1.5 py-0.5 rounded border border-indigo-800/50">
                Orchestrator
              </span>
            </div>
          </div>

          {/* User Profile / Login */}
          <div className="flex items-center space-x-4">
            {loadingUser ? (
              <div className="w-8 h-8 rounded-full bg-slate-800 animate-pulse" />
            ) : user ? (
              <div className="flex items-center space-x-3">
                <div className="flex items-center space-x-2 bg-slate-900 border border-slate-800 rounded-full py-1 px-3">
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt={user.username} className="w-6 h-6 rounded-full" />
                  ) : (
                    <GitBranch className="w-4 h-4 text-slate-400" />
                  )}
                  <span className="text-xs font-semibold text-slate-200">{user.username}</span>
                </div>
                <button
                  onClick={handleLogout}
                  title="Logout"
                  className="p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <button
                onClick={handleLogin}
                className="flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-600/20 transition"
              >
                <GitBranch className="w-4 h-4" />
                <span>Login with GitHub</span>
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Content Body */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {selectedProject ? (
          <ProjectDetail
            project={selectedProject}
            onBack={() => setSelectedProject(null)}
            onUpdateProject={(updated) => setSelectedProject(updated)}
          />
        ) : (
          <ProjectDashboard
            onSelectProject={(project) => setSelectedProject(project)}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 py-6 text-center text-xs text-slate-600">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>RivetDeploy — Isolated Docker Build Platform & Deployment Orchestrator</span>
          <div className="flex items-center space-x-4 text-slate-500">
            <span>FIFO Scheduler</span>
            <span>•</span>
            <span>Zero-Rebuild Rollbacks</span>
            <span>•</span>
            <span>Live WebSockets</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
