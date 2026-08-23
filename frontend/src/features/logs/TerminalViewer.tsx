import React, { useEffect, useState, useRef } from 'react';
import { fetchDeploymentEvents } from '../../lib/api';
import type { DeploymentEvent } from '../../lib/api';
import { Terminal, RefreshCw } from 'lucide-react';

interface TerminalViewerProps {
  deploymentId: string;
  isTerminal: boolean;
}

export const TerminalViewer: React.FC<TerminalViewerProps> = ({ deploymentId, isTerminal }) => {
  const [events, setEvents] = useState<DeploymentEvent[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Fetch historical events
  useEffect(() => {
    let isMounted = true;
    fetchDeploymentEvents(deploymentId)
      .then((data) => {
        if (isMounted) setEvents(data);
      })
      .catch((err) => console.error('Failed to load initial events:', err));
    return () => {
      isMounted = false;
    };
  }, [deploymentId]);

  // Connect to WebSocket for live streaming
  useEffect(() => {
    if (isTerminal) return; // No need to stream if already finished

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/deployments/${deploymentId}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      setIsConnected(true);
    };

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const newEvent: DeploymentEvent = {
          id: Date.now(),
          deploymentId: payload.deploymentId || deploymentId,
          eventType: payload.eventType || 'LOG',
          message: payload.message || '',
          timestamp: payload.timestamp || new Date().toISOString(),
        };

        setEvents((prev) => {
          if (prev.some((e) => e.message === newEvent.message && Math.abs(new Date(e.timestamp).getTime() - new Date(newEvent.timestamp).getTime()) < 500)) {
            return prev;
          }
          return [...prev, newEvent];
        });
      } catch (e) {
        console.error('Error parsing WS message', e);
      }
    };

    ws.onclose = () => {
      setIsConnected(false);
    };

    return () => {
      ws.close();
    };
  }, [deploymentId, isTerminal]);

  // Auto-scroll to bottom
  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [events, autoScroll]);

  return (
    <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden shadow-2xl font-mono text-xs">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800 text-slate-400">
        <div className="flex items-center space-x-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-slate-200">Deployment Logs</span>
          <span className="text-slate-500">({deploymentId})</span>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => setAutoScroll(!autoScroll)}
            className={`px-2 py-1 rounded text-[11px] transition ${
              autoScroll ? 'bg-emerald-950 text-emerald-300 border border-emerald-800' : 'bg-slate-800 text-slate-400'
            }`}
          >
            Auto-scroll: {autoScroll ? 'ON' : 'OFF'}
          </button>
          <div className="flex items-center space-x-1.5">
            <span
              className={`w-2 h-2 rounded-full ${
                isConnected ? 'bg-emerald-500 animate-pulse' : isTerminal ? 'bg-slate-500' : 'bg-amber-500'
              }`}
            />
            <span className="text-[11px]">{isConnected ? 'Live' : isTerminal ? 'Completed' : 'Connecting...'}</span>
          </div>
        </div>
      </div>

      {/* Log Output */}
      <div
        ref={scrollRef}
        className="p-4 max-h-[420px] min-h-[220px] overflow-y-auto space-y-1 select-text bg-slate-950 text-slate-300 leading-relaxed"
      >
        {events.length === 0 ? (
          <div className="flex items-center justify-center py-12 text-slate-500 space-x-2">
            <RefreshCw className="w-4 h-4 animate-spin" />
            <span>Waiting for build logs...</span>
          </div>
        ) : (
          events.map((evt, idx) => (
            <div key={idx} className="flex items-start space-x-2 hover:bg-slate-900/60 py-0.5 px-1 rounded transition">
              <span className="text-slate-600 select-none min-w-[28px] text-right">{idx + 1}</span>
              <span className="text-slate-500 text-[10px] select-none min-w-[70px]">
                {new Date(evt.timestamp).toLocaleTimeString()}
              </span>
              <span className={`min-w-[80px] text-[10px] uppercase font-semibold ${
                evt.eventType.includes('ERROR') || evt.eventType.includes('FAILED')
                  ? 'text-rose-400'
                  : evt.eventType.includes('SUCCESS') || evt.eventType.includes('ACTIVATED')
                  ? 'text-emerald-400'
                  : evt.eventType.includes('RETRY')
                  ? 'text-amber-400'
                  : 'text-sky-400'
              }`}>
                [{evt.eventType}]
              </span>
              <span className="text-slate-200 whitespace-pre-wrap break-all flex-1">{evt.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
