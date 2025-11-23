import React, { useState } from 'react';
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  List,
  Typography,
  Divider,
  IconButton,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Collapse,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  Folder as FolderIcon,
  CheckCircle as CheckCircleIcon,
  Description as DescriptionIcon,
  LocalShipping as LocalShippingIcon,
  Settings as SettingsIcon,
  ExpandLess,
  ExpandMore,
  People as PeopleIcon,
  Business as BusinessIcon,
  Rule as RuleIcon,
  Speed as SpeedIcon,
  Gavel as GavelIcon,
  Label as LabelIcon,
  Dashboard as DashboardSpeedIcon,
  Payment as PaymentIcon,
  AccountBalanceWallet as WalletIcon,
  Assessment as AssessmentIcon,
} from '@mui/icons-material';
import { useNavigate, useLocation } from 'react-router-dom';

const drawerWidth = 260;

interface MainLayoutProps {
  children: React.ReactNode;
}

interface MenuItem {
  text: string;
  icon: React.ReactElement;
  path?: string;
  children?: MenuItem[];
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [configOpen, setConfigOpen] = useState(true);
  const [checkPaymentsOpen, setCheckPaymentsOpen] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems: MenuItem[] = [
    {
      text: 'Dashboard',
      icon: <DashboardIcon />,
      path: '/dashboard',
    },
    {
      text: 'Buckets',
      icon: <FolderIcon />,
      path: '/buckets',
    },
    {
      text: 'Approvals',
      icon: <CheckCircleIcon />,
      path: '/approvals',
    },
    {
      text: 'Check Payments',
      icon: <PaymentIcon />,
      children: [
        {
          text: 'Payment Management',
          icon: <PaymentIcon />,
          path: '/check-payments',
        },
        {
          text: 'Check Reservations',
          icon: <WalletIcon />,
          path: '/check-reservations',
        },
        {
          text: 'Check Usage Status',
          icon: <AssessmentIcon />,
          path: '/check-usage',
        },
      ],
    },
    {
      text: 'Files',
      icon: <DescriptionIcon />,
      path: '/files',
    },
    {
      text: 'Delivery',
      icon: <LocalShippingIcon />,
      path: '/delivery',
    },
    {
      text: 'Configuration',
      icon: <SettingsIcon />,
      children: [
        {
          text: 'Payers',
          icon: <BusinessIcon />,
          path: '/config/payers',
        },
        {
          text: 'Payees',
          icon: <PeopleIcon />,
          path: '/config/payees',
        },
        {
          text: 'Bucketing Rules',
          icon: <RuleIcon />,
          path: '/config/rules',
        },
        {
          text: 'Thresholds',
          icon: <DashboardSpeedIcon />,
          path: '/config/thresholds-enhanced',
        },
        {
          text: 'Commit Criteria',
          icon: <GavelIcon />,
          path: '/config/criteria',
        },
        {
          text: 'Templates',
          icon: <LabelIcon />,
          path: '/config/templates',
        },
        {
          text: 'Check Payment Workflow',
          icon: <PaymentIcon />,
          path: '/config/check-payment-workflow',
        },
        {
          text: 'Check Payment Settings',
          icon: <SettingsIcon />,
          path: '/config/check-payment-settings',
        },
      ],
    },
  ];

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleNavigate = (path: string) => {
    navigate(path);
    if (mobileOpen) {
      setMobileOpen(false);
    }
  };

  const handleConfigToggle = () => {
    setConfigOpen(!configOpen);
  };

  const handleCheckPaymentsToggle = () => {
    setCheckPaymentsOpen(!checkPaymentsOpen);
  };

  const isActive = (path: string) => {
    return location.pathname === path;
  };

  const drawer = (
    <div>
      <Toolbar>
        <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 700 }}>
          EDI 835 System
        </Typography>
      </Toolbar>
      <Divider />
      <List>
        {menuItems.map((item) => (
          <React.Fragment key={item.text}>
            {item.children ? (
              <>
                <ListItemButton
                  onClick={
                    item.text === 'Configuration'
                      ? handleConfigToggle
                      : item.text === 'Check Payments'
                      ? handleCheckPaymentsToggle
                      : undefined
                  }
                >
                  <ListItemIcon>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.text} />
                  {item.text === 'Configuration'
                    ? configOpen
                      ? <ExpandLess />
                      : <ExpandMore />
                    : item.text === 'Check Payments'
                    ? checkPaymentsOpen
                      ? <ExpandLess />
                      : <ExpandMore />
                    : null}
                </ListItemButton>
                <Collapse
                  in={
                    item.text === 'Configuration'
                      ? configOpen
                      : item.text === 'Check Payments'
                      ? checkPaymentsOpen
                      : false
                  }
                  timeout="auto"
                  unmountOnExit
                >
                  <List component="div" disablePadding>
                    {item.children.map((child) => (
                      <ListItemButton
                        key={child.text}
                        sx={{ pl: 4 }}
                        selected={isActive(child.path!)}
                        onClick={() => handleNavigate(child.path!)}
                      >
                        <ListItemIcon>{child.icon}</ListItemIcon>
                        <ListItemText primary={child.text} />
                      </ListItemButton>
                    ))}
                  </List>
                </Collapse>
              </>
            ) : (
              <ListItemButton
                selected={isActive(item.path!)}
                onClick={() => handleNavigate(item.path!)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.text} />
              </ListItemButton>
            )}
          </React.Fragment>
        ))}
      </List>
    </div>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      {/* App Bar */}
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { sm: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            {menuItems.find(item => item.path === location.pathname)?.text ||
             menuItems.flatMap(item => item.children || []).find(child => child.path === location.pathname)?.text ||
             'EDI 835 Remittance Processing'}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="body2">Operations Manager</Typography>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Drawer */}
      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
        aria-label="navigation"
      >
        {/* Mobile drawer */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{
            keepMounted: true, // Better open performance on mobile
          }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
            },
          }}
        >
          {drawer}
        </Drawer>

        {/* Desktop drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      {/* Main content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
};

export default MainLayout;
