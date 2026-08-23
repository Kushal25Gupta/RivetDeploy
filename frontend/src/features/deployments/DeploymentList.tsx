import React, { useState } from 'react';
import { rollbackDeployment, cancelDeployment } from '../../lib/api';
import type { Deployment, Project } from '../../lib/api';
import { TerminalViewer } from '../logs/TerminalViewer';
import { 
  RotateCcw, 
  XCircle, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  ChevronDown, 
  ChevronUp, 
  ExternalLink,
  GitCommit
} from 'lucide-react';

interface DeploymentListProps {
  deployments: Deployment[];
  project: Project;
  onRefresh: () => void;
}

export const DeploymentList: React.FC<DeploymentListProps> = ({ deployments, project, onRefresh }) => {
  const [expandedDeploymentId, setExpandedDeploymentId] = useState<string | null>(
    deployments.length > 0 ? deployments[0].id : null
  );
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const handleRollback = async (deploymentId: string) => {
    if (!confirm('Are you sure you want to rollback to this previous deployment?')) return;
    setActionLoading(deploymentId);
    try {
      await rollbackDeployment(deploymentId);
      onRefresh();
    } catch (e: any) {
      alert(e.message || 'Rollback failed');
    } finally {
      setActionLoading(null);
    }
  };

  const handleCancel = async (deploymentId: string) => {
    if (!confirm('Cancel this running deployment?')) return;
    setActionLoading(deploymentId);
    try {
      await cancelDeployment(deploymentId);
      onRefresh();
    } catch (e: any) {
      alert(e.message || 'Cancel failed');
    } finally {
      setActionLoading(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'DEPLOYED':
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-950 text-emerald-300 border border-emerald-800/80">
            <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Deployed
          </span>
        );
      case 'QUEUED':
      case 'CLONING':
      case 'INSTALLING':
      case 'BUILDING':
      case 'UPLOADING':
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-sky-950 text-sky-300 border border-sky-800/80 animate-pulse">
            <Clock className="w-3.5 h-3.5 mr-1" /> {status}
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-800 text-slate-400 border border-slate-700">
            <XCircle className="w-3.5 h-3.5 mr-1" /> Cancelled
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-950 text-rose-300 border border-rose-800/80">
            <AlertTriangle className="w-3.5 h-3.5 mr-1" /> {status}
          </span>
        );
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-white">Deployment History ({deployments.length})</h3>
      </div>

      <div className="space-y-3">
        {deployments.map((dpl) => {
          const isCurrentActive = project.activeDeploymentId === dpl.id;
          const isExpanded = expandedDeploymentId === dpl.id;
          const isTerminal = ['DEPLOYED', 'CLONE_FAILED', 'INSTALL_FAILED', 'BUILD_FAILED', 'UPLOAD_FAILED', 'TIMEOUT', 'SYSTEM_FAILED', 'CANCELLED'].includes(dpl.status);
          const isRunning = !isTerminal;

          return (
            <div
              key={dpl.id}
              className={`bg-slate-900/90 border rounded-2xl transition overflow-hidden ${
                isCurrentActive
                  ? 'border-indigo-500/60 shadow-lg shadow-indigo-950/30'
                  : 'border-slate-800 hover:border-slate-700'
              }`}
            >
              {/* Deployment summary row */}
              <div className="p-4 flex items-center justify-between flex-wrap gap-3">
                <div className="flex items-center space-x-3">
                  <div className="space-y-0.5">
                    <div className="flex items-center space-x-2">
                      <span className="font-semibold text-slate-100">{dpl.id}</span>
                      {isCurrentActive && (
                        <span className="px-2 py-0.5 text-[10px] font-bold bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 rounded-md">
                          ACTIVE
                        </span>
                      )}
                      {getStatusBadge(dpl.status)}
                    </div>
                    <div className="flex items-center space-x-3 text-xs text-slate-400 font-mono">
                      <span className="flex items-center space-x-1">
                        <GitCommit className="w-3.5 h-3.5" />
                        <span>{dpl.commitSha?.substring(0, 7) || 'HEAD'}</span>
                      </span>
                      <span>•</span>
                      <span>{new Date(dpl.createdAt).toLocaleString()}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  {isRunning && (
                    <button
                      onClick={() => handleCancel(dpl.id)}
                      disabled={actionLoading === dpl.id}
                      className="flex items-center space-x-1 px-3 py-1.5 rounded-lg text-xs font-medium bg-rose-950 text-rose-300 border border-rose-800 hover:bg-rose-900 transition disabled:opacity-50"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Cancel</span>
                    </button>
                  )}

                  {!isCurrentActive && dpl.status === 'DEPLOYED' && (
                    <button
                      onClick={() => handleRollback(dpl.id)}
                      disabled={actionLoading === dpl.id}
                      className="flex items-center space-x-1 px-3 py-1.5 rounded-lg text-xs font-medium bg-indigo-950 text-indigo-300 border border-indigo-800 hover:bg-indigo-900 transition disabled:opacity-50"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      <span>Rollback</span>
                    </button>
                  )}

                  {isCurrentActive && (
                    <a
                      href={`/sites/projects/${project.id}/current/`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center space-x-1 px-3 py-1.5 rounded-lg text-xs font-medium bg-emerald-950 text-emerald-300 border border-emerald-800 hover:bg-emerald-900 transition"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span>Visit Site</span>
                    </a>
                  )}

                  <button
                    onClick={() => setExpandedDeploymentId(isExpanded ? null : dpl.id)}
                    className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
                  >
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Terminal Logs (if expanded) */}
              {isExpanded && (
                <div className="px-4 pb-4 pt-1 border-t border-slate-800/80">
                  <TerminalViewer deploymentId={dpl.id} isTerminal={isTerminal} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
