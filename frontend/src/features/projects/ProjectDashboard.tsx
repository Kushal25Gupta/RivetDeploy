import React, { useState, useEffect } from 'react';
import { fetchProjects } from '../../lib/api';
import type { Project } from '../../lib/api';
import { ProjectModal } from './ProjectModal';
import { 
  Plus, 
  Layers, 
  GitBranch, 
  Cpu, 
  Server, 
  ArrowRight,
  ShieldCheck
} from 'lucide-react';

interface ProjectDashboardProps {
  onSelectProject: (project: Project) => void;
}

export const ProjectDashboard: React.FC<ProjectDashboardProps> = ({ onSelectProject }) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const loadProjects = async () => {
    try {
      const data = await fetchProjects();
      setProjects(data);
    } catch (e) {
      console.error('Failed to load projects', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects();
  }, []);

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      {/* Hero / Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Projects</h1>
          <p className="text-sm text-slate-400 mt-1">
            Manage your Git deployments, monitor builds, and perform instant zero-rebuild rollbacks.
          </p>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="flex items-center space-x-2 px-5 py-2.5 rounded-2xl text-sm font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-600/30 transition self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>New Project</span>
        </button>
      </div>

      {/* Stats Summary Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-5 backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase text-slate-400 tracking-wider">Total Projects</span>
            <Layers className="w-4 h-4 text-indigo-400" />
          </div>
          <p className="text-2xl font-bold text-white mt-2">{projects.length}</p>
        </div>

        <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-5 backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase text-slate-400 tracking-wider">Build Isolation</span>
            <Server className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-sm font-medium text-emerald-300 mt-2 flex items-center space-x-1.5">
            <ShieldCheck className="w-4 h-4" />
            <span>Docker & Nixpacks Sandboxed</span>
          </p>
        </div>

        <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-5 backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase text-slate-400 tracking-wider">Queue Strategy</span>
            <Cpu className="w-4 h-4 text-sky-400" />
          </div>
          <p className="text-sm font-medium text-sky-300 mt-2">
            Strict FIFO with Exponential Backoff
          </p>
        </div>
      </div>

      {/* Projects Grid */}
      {loading ? (
        <div className="text-center py-20 text-slate-500">Loading projects...</div>
      ) : projects.length === 0 ? (
        <div className="bg-slate-900/40 border border-dashed border-slate-800 rounded-3xl p-12 text-center space-y-4">
          <Layers className="w-12 h-12 text-slate-600 mx-auto" />
          <div className="space-y-1">
            <h3 className="text-lg font-medium text-slate-200">No projects yet</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Connect a GitHub repository to trigger isolated builds, instant rollbacks, and live log streaming.
            </p>
          </div>
          <button
            onClick={() => setIsModalOpen(true)}
            className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold bg-indigo-600 text-white hover:bg-indigo-500 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Create First Project</span>
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {projects.map((proj) => (
            <div
              key={proj.id}
              onClick={() => onSelectProject(proj)}
              className="group bg-slate-900/80 border border-slate-800 hover:border-indigo-500/50 rounded-2xl p-6 transition-all duration-200 hover:shadow-xl hover:shadow-indigo-950/20 cursor-pointer flex flex-col justify-between"
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between">
                  <h3 className="text-lg font-bold text-white group-hover:text-indigo-300 transition line-clamp-1">
                    {proj.name}
                  </h3>
                  <span className="flex items-center space-x-1 px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
                    <GitBranch className="w-3 h-3 text-indigo-400" />
                    <span>{proj.branch}</span>
                  </span>
                </div>

                <p className="text-xs text-slate-400 font-mono line-clamp-1">
                  {proj.repositoryUrl}
                </p>

                {proj.activeDeploymentId ? (
                  <div className="flex items-center space-x-2 text-xs text-emerald-400">
                    <span className="w-2 h-2 rounded-full bg-emerald-400" />
                    <span>Active: {proj.activeDeploymentId.substring(0, 10)}...</span>
                  </div>
                ) : (
                  <div className="text-xs text-slate-500">Not yet deployed</div>
                )}
              </div>

              <div className="mt-6 pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400 group-hover:text-indigo-300 transition">
                <span>View Deployments</span>
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Project Creation Modal */}
      <ProjectModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={(newProject) => {
          setProjects([newProject, ...projects]);
          onSelectProject(newProject);
        }}
      />
    </div>
  );
};
