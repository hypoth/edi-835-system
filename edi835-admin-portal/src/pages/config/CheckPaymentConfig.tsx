import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Switch,
  TextField,
  Button,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Tooltip,
  FormControlLabel,
} from '@mui/material';
import {
  Edit as EditIcon,
  Save as SaveIcon,
  Cancel as CancelIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Refresh as RefreshIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import {
  checkPaymentConfigService,
  CheckPaymentConfig,
  UpdateCheckPaymentConfigRequest,
} from '../../services/checkPaymentConfigService';

const CheckPaymentConfigPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [editValue, setEditValue] = React.useState<string>('');
  const [editDescription, setEditDescription] = React.useState<string>('');
  const [confirmDialogOpen, setConfirmDialogOpen] = React.useState(false);
  const [pendingToggle, setPendingToggle] = React.useState<CheckPaymentConfig | null>(null);

  // Fetch all configs
  const { data: configs, isLoading, error, refetch } = useQuery({
    queryKey: ['check-payment-configs'],
    queryFn: checkPaymentConfigService.getAllConfigs,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateCheckPaymentConfigRequest }) =>
      checkPaymentConfigService.updateConfig(id, request),
    onSuccess: () => {
      toast.success('Configuration updated successfully');
      queryClient.invalidateQueries({ queryKey: ['check-payment-configs'] });
      setEditingId(null);
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to update configuration');
    },
  });

  // Toggle active mutation
  const toggleMutation = useMutation({
    mutationFn: (id: number) => checkPaymentConfigService.toggleActive(id),
    onSuccess: () => {
      toast.success('Configuration status toggled');
      queryClient.invalidateQueries({ queryKey: ['check-payment-configs'] });
      setConfirmDialogOpen(false);
      setPendingToggle(null);
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to toggle configuration');
    },
  });

  const handleEdit = (config: CheckPaymentConfig) => {
    setEditingId(config.id);
    setEditValue(config.configValue);
    setEditDescription(config.description || '');
  };

  const handleSave = (id: string) => {
    updateMutation.mutate({
      id,
      request: {
        configValue: editValue,
        description: editDescription,
        updatedBy: 'admin',
      },
    });
  };

  const handleCancel = () => {
    setEditingId(null);
    setEditValue('');
    setEditDescription('');
  };

  const handleToggleClick = (config: CheckPaymentConfig) => {
    setPendingToggle(config);
    setConfirmDialogOpen(true);
  };

  const handleToggleConfirm = () => {
    if (pendingToggle) {
      toggleMutation.mutate(pendingToggle.id);
    }
  };

  const getValueTypeColor = (valueType: string): 'primary' | 'secondary' | 'success' | 'warning' => {
    switch (valueType) {
      case 'INTEGER':
        return 'primary';
      case 'BOOLEAN':
        return 'success';
      case 'EMAIL':
        return 'warning';
      default:
        return 'secondary';
    }
  };

  const renderEditField = (config: CheckPaymentConfig) => {
    switch (config.valueType) {
      case 'BOOLEAN':
        return (
          <FormControlLabel
            control={
              <Switch
                checked={editValue === 'true' || editValue === '1' || editValue === 'yes'}
                onChange={(e) => setEditValue(e.target.checked ? 'true' : 'false')}
              />
            }
            label={editValue === 'true' || editValue === '1' || editValue === 'yes' ? 'Yes' : 'No'}
          />
        );
      case 'INTEGER':
        return (
          <TextField
            type="number"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            size="small"
            fullWidth
            inputProps={{ min: 0 }}
          />
        );
      case 'EMAIL':
        return (
          <TextField
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            size="small"
            fullWidth
            multiline
            rows={2}
            placeholder="email1@example.com, email2@example.com"
            helperText="Comma-separated email addresses"
          />
        );
      default:
        return (
          <TextField
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            size="small"
            fullWidth
          />
        );
    }
  };

  const renderDisplayValue = (config: CheckPaymentConfig) => {
    switch (config.valueType) {
      case 'BOOLEAN':
        const isTruthy = config.configValue === 'true' || config.configValue === '1' || config.configValue === 'yes';
        return (
          <Chip
            label={isTruthy ? 'Yes' : 'No'}
            color={isTruthy ? 'success' : 'default'}
            size="small"
          />
        );
      case 'EMAIL':
        const emails = config.configValue.split(',').map((e) => e.trim()).filter(Boolean);
        return (
          <Box>
            {emails.map((email, idx) => (
              <Chip
                key={idx}
                label={email}
                size="small"
                variant="outlined"
                sx={{ mr: 0.5, mb: 0.5 }}
              />
            ))}
          </Box>
        );
      case 'INTEGER':
        return (
          <Typography variant="body2" fontFamily="monospace" fontWeight={600}>
            {config.configValue}
          </Typography>
        );
      default:
        return (
          <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
            {config.configValue}
          </Typography>
        );
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error">
        Failed to load check payment configurations. Please try again later.
      </Alert>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Box>
          <Typography variant="h4" fontWeight={600} gutterBottom>
            <SettingsIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
            Check Payment Configuration
          </Typography>
          <Typography variant="body2" color="textSecondary">
            Manage system-wide settings for check payment operations
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={() => refetch()}
        >
          Refresh
        </Button>
      </Box>

      {/* Configuration Table */}
      <Card>
        <CardContent>
          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow sx={{ backgroundColor: 'grey.100' }}>
                  <TableCell width="25%"><strong>Setting</strong></TableCell>
                  <TableCell width="10%"><strong>Type</strong></TableCell>
                  <TableCell width="30%"><strong>Value</strong></TableCell>
                  <TableCell width="20%"><strong>Description</strong></TableCell>
                  <TableCell width="8%"><strong>Active</strong></TableCell>
                  <TableCell width="7%"><strong>Actions</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {configs?.map((config) => (
                  <TableRow key={config.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {config.displayName}
                      </Typography>
                      <Typography variant="caption" color="textSecondary" fontFamily="monospace">
                        {config.configKey}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={config.valueType}
                        size="small"
                        color={getValueTypeColor(config.valueType)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      {editingId === config.id ? (
                        renderEditField(config)
                      ) : (
                        <Box display="flex" alignItems="center" gap={1}>
                          {renderDisplayValue(config)}
                          {!config.isValid && (
                            <Tooltip title="Invalid value for this type">
                              <ErrorIcon color="error" fontSize="small" />
                            </Tooltip>
                          )}
                        </Box>
                      )}
                    </TableCell>
                    <TableCell>
                      {editingId === config.id ? (
                        <TextField
                          value={editDescription}
                          onChange={(e) => setEditDescription(e.target.value)}
                          size="small"
                          fullWidth
                          multiline
                          rows={2}
                        />
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          {config.description || '-'}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Switch
                        checked={config.isActive}
                        onChange={() => handleToggleClick(config)}
                        color="success"
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      {editingId === config.id ? (
                        <Box display="flex" gap={0.5}>
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => handleSave(config.id)}
                            disabled={updateMutation.isPending}
                          >
                            <SaveIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            color="default"
                            onClick={handleCancel}
                            disabled={updateMutation.isPending}
                          >
                            <CancelIcon fontSize="small" />
                          </IconButton>
                        </Box>
                      ) : (
                        <IconButton
                          size="small"
                          color="primary"
                          onClick={() => handleEdit(config)}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Legend */}
          <Box mt={2} p={2} bgcolor="grey.50" borderRadius={1}>
            <Typography variant="subtitle2" gutterBottom>
              Value Types:
            </Typography>
            <Box display="flex" gap={2} flexWrap="wrap">
              <Box display="flex" alignItems="center" gap={0.5}>
                <Chip label="INTEGER" size="small" color="primary" variant="outlined" />
                <Typography variant="caption">Numeric values</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={0.5}>
                <Chip label="BOOLEAN" size="small" color="success" variant="outlined" />
                <Typography variant="caption">Yes/No flags</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={0.5}>
                <Chip label="EMAIL" size="small" color="warning" variant="outlined" />
                <Typography variant="caption">Comma-separated emails</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={0.5}>
                <Chip label="STRING" size="small" color="secondary" variant="outlined" />
                <Typography variant="caption">Text values</Typography>
              </Box>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Confirm Toggle Dialog */}
      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>
          Confirm Status Change
        </DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to {pendingToggle?.isActive ? 'disable' : 'enable'} the configuration{' '}
            <strong>{pendingToggle?.displayName}</strong>?
          </Typography>
          {pendingToggle?.isActive && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              Disabling this configuration may affect system behavior.
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleToggleConfirm}
            variant="contained"
            color={pendingToggle?.isActive ? 'error' : 'success'}
            disabled={toggleMutation.isPending}
          >
            {pendingToggle?.isActive ? 'Disable' : 'Enable'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CheckPaymentConfigPage;
