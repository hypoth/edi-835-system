import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

// Layout
import MainLayout from './components/layout/MainLayout';

// Pages
import Dashboard from './pages/Dashboard';
import BucketList from './pages/buckets/BucketList';
import BucketDetails from './pages/buckets/BucketDetails';
import ApprovalQueue from './pages/approvals/ApprovalQueue';
import FileList from './pages/files/FileList';
import FileDetails from './pages/files/FileDetails';
import DeliveryTracking from './pages/delivery/DeliveryTracking';
import PayersConfig from './pages/config/PayersConfig';
import PayeesConfig from './pages/config/PayeesConfig';
import BucketingRulesConfig from './pages/config/BucketingRulesConfig';
import ThresholdsConfigEnhanced from './pages/config/ThresholdsConfigEnhanced';
import CommitCriteriaConfig from './pages/config/CommitCriteriaConfig';
import FileNamingTemplatesConfig from './pages/config/FileNamingTemplatesConfig';

// Create theme
const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
    background: {
      default: '#f5f5f5',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h4: {
      fontWeight: 600,
    },
    h5: {
      fontWeight: 600,
    },
    h6: {
      fontWeight: 600,
    },
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          borderRadius: 6,
        },
      },
    },
  },
});

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000, // 5 minutes
    },
  },
});

const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router>
          <MainLayout>
            <Routes>
              {/* Dashboard */}
              <Route path="/" element={<Dashboard />} />
              <Route path="/dashboard" element={<Dashboard />} />

              {/* Buckets */}
              <Route path="/buckets" element={<BucketList />} />
              <Route path="/buckets/:bucketId" element={<BucketDetails />} />

              {/* Approvals */}
              <Route path="/approvals" element={<ApprovalQueue />} />

              {/* Files */}
              <Route path="/files" element={<FileList />} />
              <Route path="/files/:fileId" element={<FileDetails />} />

              {/* Delivery */}
              <Route path="/delivery" element={<DeliveryTracking />} />

              {/* Configuration */}
              <Route path="/config/payers" element={<PayersConfig />} />
              <Route path="/config/payees" element={<PayeesConfig />} />
              <Route path="/config/rules" element={<BucketingRulesConfig />} />
              <Route path="/config/thresholds-enhanced" element={<ThresholdsConfigEnhanced />} />
              <Route path="/config/criteria" element={<CommitCriteriaConfig />} />
              <Route path="/config/templates" element={<FileNamingTemplatesConfig />} />

              {/* Redirect unknown routes to dashboard */}
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </MainLayout>
        </Router>
        <ToastContainer
          position="top-right"
          autoClose={3000}
          hideProgressBar={false}
          newestOnTop
          closeOnClick
          rtl={false}
          pauseOnFocusLoss
          draggable
          pauseOnHover
        />
      </ThemeProvider>
    </QueryClientProvider>
  );
};

export default App;
