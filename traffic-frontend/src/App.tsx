import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import Layout from '@/components/Layout';
import Login from '@/pages/Login';
import Dashboard from '@/pages/Dashboard';
import Cameras from '@/pages/Cameras';
import Alerts from '@/pages/Alerts';
import WorkOrders from '@/pages/WorkOrders';
import Departments from '@/pages/Departments';
import GeoFences from '@/pages/GeoFences';
import Tracks from '@/pages/Tracks';
import TrafficStatistics from '@/pages/TrafficStatistics';
import EventVideos from '@/pages/EventVideos';
import PredictionDashboard from '@/pages/PredictionDashboard';
import Rules from '@/pages/Rules';
import DecisionTables from '@/pages/DecisionTables';
import RuleExecutionLogs from '@/pages/RuleExecutionLogs';
import NotifyChannels from '@/pages/NotifyChannels';
import NotifyTemplates from '@/pages/NotifyTemplates';
import NotifyRules from '@/pages/NotifyRules';
import OnDutyPage from '@/pages/OnDuty';
import NotifyLogs from '@/pages/NotifyLogs';
import PlateRecognitions from '@/pages/PlateRecognitions';
import PolicePushes from '@/pages/PolicePushes';
import PoliceSystemConfigs from '@/pages/PoliceSystemConfigs';
import EdgeNodes from '@/pages/EdgeNodes';
import VirtualPatrol from '@/pages/VirtualPatrol';
import StormSuppression from '@/pages/StormSuppression';
import ConstructionZones from '@/pages/ConstructionZones';
import AudioEvents from '@/pages/AudioEvents';
import React from 'react';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Layout>{children}</Layout>;
};

const PublicRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuthStore();

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

export default function App() {
  return (
    <Router>
      <Routes>
        <Route
          path="/login"
          element={
            <PublicRoute>
              <Login />
            </PublicRoute>
          }
        />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/cameras"
          element={
            <ProtectedRoute>
              <Cameras />
            </ProtectedRoute>
          }
        />
        <Route
          path="/alerts"
          element={
            <ProtectedRoute>
              <Alerts />
            </ProtectedRoute>
          }
        />
        <Route
          path="/work-orders"
          element={
            <ProtectedRoute>
              <WorkOrders />
            </ProtectedRoute>
          }
        />
        <Route
          path="/departments"
          element={
            <ProtectedRoute>
              <Departments />
            </ProtectedRoute>
          }
        />
        <Route
          path="/geo-fences"
          element={
            <ProtectedRoute>
              <GeoFences />
            </ProtectedRoute>
          }
        />
        <Route
          path="/construction-zones"
          element={
            <ProtectedRoute>
              <ConstructionZones />
            </ProtectedRoute>
          }
        />
        <Route
          path="/tracks"
          element={
            <ProtectedRoute>
              <Tracks />
            </ProtectedRoute>
          }
        />
        <Route
          path="/traffic-statistics"
          element={
            <ProtectedRoute>
              <TrafficStatistics />
            </ProtectedRoute>
          }
        />
        <Route
          path="/event-videos"
          element={
            <ProtectedRoute>
              <EventVideos />
            </ProtectedRoute>
          }
        />
        <Route
          path="/prediction"
          element={
            <ProtectedRoute>
              <PredictionDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rules"
          element={
            <ProtectedRoute>
              <Rules />
            </ProtectedRoute>
          }
        />
        <Route
          path="/decision-tables"
          element={
            <ProtectedRoute>
              <DecisionTables />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rule-logs"
          element={
            <ProtectedRoute>
              <RuleExecutionLogs />
            </ProtectedRoute>
          }
        />
        <Route
          path="/notify-channels"
          element={
            <ProtectedRoute>
              <NotifyChannels />
            </ProtectedRoute>
          }
        />
        <Route
          path="/notify-templates"
          element={
            <ProtectedRoute>
              <NotifyTemplates />
            </ProtectedRoute>
          }
        />
        <Route
          path="/notify-rules"
          element={
            <ProtectedRoute>
              <NotifyRules />
            </ProtectedRoute>
          }
        />
        <Route
          path="/on-duty"
          element={
            <ProtectedRoute>
              <OnDutyPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/notify-logs"
          element={
            <ProtectedRoute>
              <NotifyLogs />
            </ProtectedRoute>
          }
        />
        <Route
          path="/plate-recognitions"
          element={
            <ProtectedRoute>
              <PlateRecognitions />
            </ProtectedRoute>
          }
        />
        <Route
          path="/police-pushes"
          element={
            <ProtectedRoute>
              <PolicePushes />
            </ProtectedRoute>
          }
        />
        <Route
          path="/police-system-configs"
          element={
            <ProtectedRoute>
              <PoliceSystemConfigs />
            </ProtectedRoute>
          }
        />
        <Route
          path="/edge-nodes"
          element={
            <ProtectedRoute>
              <EdgeNodes />
            </ProtectedRoute>
          }
        />
        <Route
          path="/virtual-patrol"
          element={
            <ProtectedRoute>
              <VirtualPatrol />
            </ProtectedRoute>
          }
        />
        <Route
          path="/storm-suppression"
          element={
            <ProtectedRoute>
              <StormSuppression />
            </ProtectedRoute>
          }
        />
        <Route
          path="/audio-events"
          element={
            <ProtectedRoute>
              <AudioEvents />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}
