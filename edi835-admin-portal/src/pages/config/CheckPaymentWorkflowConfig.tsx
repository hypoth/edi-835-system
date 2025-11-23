import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormControlLabel,
  Switch,
  IconButton,
  Chip,
  CircularProgress,
  Alert,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { checkPaymentWorkflowService } from '../../services/checkPaymentWorkflowService';
import { thresholdService } from '../../services/thresholdService';
import {
  CheckPaymentWorkflowConfig,
  CheckPaymentWorkflowConfigRequest,
  WorkflowMode,
  AssignmentMode,
  Threshold,
} from '../../types/models';

const CheckPaymentWorkflowConfigPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<CheckPaymentWorkflowConfig | null>(null);
  const [formData, setFormData] = useState<CheckPaymentWorkflowConfigRequest>({
    configName: '',
    workflowMode: 'NONE',
    assignmentMode: 'MANUAL',
    requireAcknowledgment: false,
    linkedThresholdId: '',
    description: '',
    isActive: true,
  });

  // Fetch workflow configurations
  const { data: configs, isLoading, refetch } = useQuery<CheckPaymentWorkflowConfig[]>({
    queryKey: ['workflowConfigs'],
    queryFn: checkPaymentWorkflowService.getAllWorkflowConfigs,
  });

  // Fetch thresholds for dropdown
  const { data: thresholds } = useQuery<Threshold[]>({
    queryKey: ['thresholds'],
    queryFn: thresholdService.getAllThresholds,
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: checkPaymentWorkflowService.createWorkflowConfig,
    onSuccess: () => {
      toast.success('Workflow configuration created successfully');
      queryClient.invalidateQueries({ queryKey: ['workflowConfigs'] });
      handleCloseDialog();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to create workflow configuration');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: CheckPaymentWorkflowConfigRequest }) =>
      checkPaymentWorkflowService.updateWorkflowConfig(id, request),
    onSuccess: () => {
      toast.success('Workflow configuration updated successfully');
      queryClient.invalidateQueries({ queryKey: ['workflowConfigs'] });
      handleCloseDialog();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to update workflow configuration');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: checkPaymentWorkflowService.deleteWorkflowConfig,
    onSuccess: () => {
      toast.success('Workflow configuration deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['workflowConfigs'] });
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to delete workflow configuration');
    },
  });

  const handleOpenDialog = (config?: CheckPaymentWorkflowConfig) => {
    if (config) {
      setEditingConfig(config);
      setFormData({
        configName: config.configName,
        workflowMode: config.workflowMode,
        assignmentMode: config.assignmentMode,
        requireAcknowledgment: config.requireAcknowledgment,
        linkedThresholdId: config.linkedThresholdId,
        description: config.description || '',
        isActive: config.isActive,
      });
    } else {
      setEditingConfig(null);
      setFormData({
        configName: '',
        workflowMode: 'NONE',
        assignmentMode: 'MANUAL',
        requireAcknowledgment: false,
        linkedThresholdId: '',
        description: '',
        isActive: true,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingConfig(null);
  };

  const handleSubmit = () => {
    if (!formData.configName.trim()) {
      toast.warning('Configuration name is required');
      return;
    }

    if (!formData.linkedThresholdId) {
      toast.warning('Please select a threshold');
      return;
    }

    if (editingConfig) {
      updateMutation.mutate({
        id: editingConfig.id,
        request: { ...formData, updatedBy: 'admin' },
      });
    } else {
      createMutation.mutate({ ...formData, createdBy: 'admin' });
    }
  };

  const handleDelete = (id: string) => {
    if (window.confirm('Are you sure you want to delete this workflow configuration?')) {
      deleteMutation.mutate(id);
    }
  };

  const getWorkflowModeColor = (mode: string) => {
    switch (mode) {
      case 'NONE':
        return 'default';
      case 'SEPARATE':
        return 'primary';
      case 'COMBINED':
        return 'secondary';
      default:
        return 'default';
    }
  };

  const getWorkflowModeDescription = (mode: string) => {
    switch (mode) {
      case 'NONE':
        return 'No check payment (EFT/other)';
      case 'SEPARATE':
        return 'Approve → Assign check separately';
      case 'COMBINED':
        return 'Approve + assign check in one step';
      default:
        return mode;
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" p={4}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box mb={3} display="flex" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={600} gutterBottom>
            Check Payment Workflow Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Configure how check payments are handled during bucket approval
          </Typography>
        </Box>
        <Box>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
            sx={{ mr: 1 }}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={() => handleOpenDialog()}
          >
            Add Configuration
          </Button>
        </Box>
      </Box>

      {/* Info Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          <strong>Workflow Modes:</strong><br />
          • <strong>NONE</strong>: No check payment required (EFT or other payment method)<br />
          • <strong>SEPARATE</strong>: Approve bucket first, then assign check in a separate step<br />
          • <strong>COMBINED</strong>: Approve and assign check in a single dialog
        </Typography>
      </Alert>

      {/* Configurations Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Configuration Name</strong></TableCell>
                <TableCell><strong>Workflow Mode</strong></TableCell>
                <TableCell><strong>Assignment Mode</strong></TableCell>
                <TableCell><strong>Linked Threshold</strong></TableCell>
                <TableCell><strong>Bucketing Rule</strong></TableCell>
                <TableCell><strong>Require Ack</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {configs && configs.length > 0 ? (
                configs.map((config) => (
                  <TableRow key={config.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>
                        {config.configName}
                      </Typography>
                      {config.description && (
                        <Typography variant="caption" color="textSecondary">
                          {config.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={config.workflowMode}
                        color={getWorkflowModeColor(config.workflowMode)}
                        size="small"
                      />
                      <Typography variant="caption" display="block" color="textSecondary">
                        {getWorkflowModeDescription(config.workflowMode)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={config.assignmentMode} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {config.linkedThresholdName || config.linkedThresholdId.substring(0, 8)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {config.linkedBucketingRuleName || 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {config.requireAcknowledgment ? (
                        <Chip label="Yes" color="warning" size="small" />
                      ) : (
                        <Chip label="No" size="small" variant="outlined" />
                      )}
                    </TableCell>
                    <TableCell>
                      {config.isActive ? (
                        <Chip label="Active" color="success" size="small" />
                      ) : (
                        <Chip label="Inactive" size="small" />
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => handleOpenDialog(config)}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(config.id)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No workflow configurations found. Click "Add Configuration" to create one.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="md" fullWidth>
        <DialogTitle>
          {editingConfig ? 'Edit Workflow Configuration' : 'Add Workflow Configuration'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Configuration Name"
              value={formData.configName}
              onChange={(e) => setFormData({ ...formData, configName: e.target.value })}
              sx={{ mb: 2 }}
              required
            />

            <FormControl fullWidth sx={{ mb: 2 }} required>
              <InputLabel>Linked Threshold</InputLabel>
              <Select
                value={formData.linkedThresholdId}
                onChange={(e) => setFormData({ ...formData, linkedThresholdId: e.target.value })}
                label="Linked Threshold"
                disabled={!!editingConfig}
              >
                {thresholds?.map((threshold) => (
                  <MenuItem key={threshold.id || threshold.thresholdId} value={threshold.id || threshold.thresholdId}>
                    {threshold.thresholdName} ({threshold.thresholdType})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth sx={{ mb: 2 }} required>
              <InputLabel>Workflow Mode</InputLabel>
              <Select
                value={formData.workflowMode}
                onChange={(e) => setFormData({ ...formData, workflowMode: e.target.value })}
                label="Workflow Mode"
              >
                <MenuItem value="NONE">NONE - No check payment (EFT/other)</MenuItem>
                <MenuItem value="SEPARATE">SEPARATE - Approve then assign check</MenuItem>
                <MenuItem value="COMBINED">COMBINED - Approve + assign in one step</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth sx={{ mb: 2 }} required>
              <InputLabel>Assignment Mode</InputLabel>
              <Select
                value={formData.assignmentMode}
                onChange={(e) => setFormData({ ...formData, assignmentMode: e.target.value })}
                label="Assignment Mode"
              >
                <MenuItem value="MANUAL">MANUAL - User enters check details</MenuItem>
                <MenuItem value="AUTO">AUTO - Auto-assign from reservations</MenuItem>
                <MenuItem value="BOTH">BOTH - User can choose</MenuItem>
              </Select>
            </FormControl>

            <FormControlLabel
              control={
                <Switch
                  checked={formData.requireAcknowledgment}
                  onChange={(e) =>
                    setFormData({ ...formData, requireAcknowledgment: e.target.checked })
                  }
                />
              }
              label="Require Acknowledgment Before EDI Generation"
              sx={{ mb: 2 }}
            />

            <TextField
              fullWidth
              multiline
              rows={3}
              label="Description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              sx={{ mb: 2 }}
            />

            <FormControlLabel
              control={
                <Switch
                  checked={formData.isActive}
                  onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                />
              }
              label="Active"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            color="primary"
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? (
              <CircularProgress size={24} />
            ) : editingConfig ? (
              'Update'
            ) : (
              'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CheckPaymentWorkflowConfigPage;
