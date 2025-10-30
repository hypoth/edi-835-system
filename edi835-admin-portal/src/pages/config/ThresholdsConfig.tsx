import React, { useState } from 'react';
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
  Button,
  IconButton,
  CircularProgress,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Switch,
  FormControlLabel,
  Chip,
  MenuItem,
  Alert,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { configurationService } from '../../services/configurationService';
import { GenerationThreshold, ThresholdType, BucketingRule } from '../../types/models';
import { toast } from 'react-toastify';

const ThresholdsConfig: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingThreshold, setEditingThreshold] = useState<GenerationThreshold | null>(null);
  const [formData, setFormData] = useState<Partial<GenerationThreshold>>({
    thresholdName: '',
    thresholdType: ThresholdType.CLAIM_COUNT,
    maxClaims: undefined,
    maxAmount: undefined,
    timeDuration: undefined,
    isActive: true,
  });

  // Fetch thresholds
  const { data: thresholds, isLoading, refetch } = useQuery<GenerationThreshold[]>({
    queryKey: ['thresholds'],
    queryFn: configurationService.getAllThresholds,
  });

  // Fetch bucketing rules for dropdown
  const { data: bucketingRules } = useQuery<BucketingRule[]>({
    queryKey: ['bucketingRules'],
    queryFn: configurationService.getAllBucketingRules,
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (threshold: Partial<GenerationThreshold>) =>
      configurationService.createThreshold(threshold),
    onSuccess: () => {
      toast.success('Threshold created successfully');
      queryClient.invalidateQueries({ queryKey: ['thresholds'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to create threshold');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<GenerationThreshold> }) =>
      configurationService.updateThreshold(id, data),
    onSuccess: () => {
      toast.success('Threshold updated successfully');
      queryClient.invalidateQueries({ queryKey: ['thresholds'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to update threshold');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => configurationService.deleteThreshold(id),
    onSuccess: () => {
      toast.success('Threshold deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['thresholds'] });
      setDeleteDialogOpen(false);
      setEditingThreshold(null);
    },
    onError: () => {
      toast.error('Failed to delete threshold');
    },
  });

  const handleOpenDialog = (threshold?: GenerationThreshold) => {
    if (threshold) {
      setEditingThreshold(threshold);
      setFormData(threshold);
    } else {
      setEditingThreshold(null);
      setFormData({
        thresholdName: '',
        thresholdType: ThresholdType.CLAIM_COUNT,
        maxClaims: undefined,
        maxAmount: undefined,
        timeDuration: undefined,
        isActive: true,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingThreshold(null);
  };

  const handleSubmit = () => {
    if (!formData.thresholdName?.trim()) {
      toast.warning('Threshold name is required');
      return;
    }

    // Validation based on threshold type
    if (
      (formData.thresholdType === ThresholdType.CLAIM_COUNT || formData.thresholdType === ThresholdType.HYBRID) &&
      (!formData.maxClaims || formData.maxClaims <= 0)
    ) {
      toast.warning('Max claims must be greater than 0');
      return;
    }

    if (
      (formData.thresholdType === ThresholdType.AMOUNT || formData.thresholdType === ThresholdType.HYBRID) &&
      (!formData.maxAmount || formData.maxAmount <= 0)
    ) {
      toast.warning('Max amount must be greater than 0');
      return;
    }

    if (
      (formData.thresholdType === ThresholdType.TIME || formData.thresholdType === ThresholdType.HYBRID) &&
      !formData.timeDuration
    ) {
      toast.warning('Time duration is required');
      return;
    }

    if (editingThreshold) {
      updateMutation.mutate({ id: editingThreshold.thresholdId, data: formData });
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleDeleteClick = (threshold: GenerationThreshold) => {
    setEditingThreshold(threshold);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (editingThreshold) {
      deleteMutation.mutate(editingThreshold.thresholdId);
    }
  };

  const getThresholdTypeLabel = (type: ThresholdType): string => {
    switch (type) {
      case ThresholdType.CLAIM_COUNT:
        return 'Claim Count';
      case ThresholdType.AMOUNT:
        return 'Amount';
      case ThresholdType.TIME:
        return 'Time-Based';
      case ThresholdType.HYBRID:
        return 'Hybrid';
      default:
        return type;
    }
  };

  const getThresholdTypeColor = (type: ThresholdType): 'primary' | 'secondary' | 'info' | 'warning' => {
    switch (type) {
      case ThresholdType.CLAIM_COUNT:
        return 'primary';
      case ThresholdType.AMOUNT:
        return 'secondary';
      case ThresholdType.TIME:
        return 'info';
      case ThresholdType.HYBRID:
        return 'warning';
      default:
        return 'primary';
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
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
            Generation Thresholds Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Define thresholds that trigger EDI file generation
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
            startIcon={<AddIcon />}
            onClick={() => handleOpenDialog()}
          >
            Add Threshold
          </Button>
        </Box>
      </Box>

      {/* Info Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          Thresholds automatically trigger file generation when conditions are met.
          Hybrid thresholds use OR logic (any condition triggers generation).
        </Typography>
      </Alert>

      {/* Thresholds Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Threshold Name</strong></TableCell>
                <TableCell><strong>Type</strong></TableCell>
                <TableCell><strong>Max Claims</strong></TableCell>
                <TableCell><strong>Max Amount</strong></TableCell>
                <TableCell><strong>Time Duration</strong></TableCell>
                <TableCell><strong>Linked Rule</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {thresholds && thresholds.length > 0 ? (
                thresholds.map((threshold) => (
                  <TableRow key={threshold.thresholdId} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {threshold.thresholdName}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={getThresholdTypeLabel(threshold.thresholdType)}
                        color={getThresholdTypeColor(threshold.thresholdType)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {threshold.maxClaims || 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {threshold.maxAmount ? `$${threshold.maxAmount.toLocaleString()}` : 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {threshold.timeDuration || 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {threshold.linkedBucketingRule?.ruleName || 'All Rules'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={threshold.isActive ? 'Active' : 'Inactive'}
                        color={threshold.isActive ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => handleOpenDialog(threshold)}
                          color="primary"
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteClick(threshold)}
                          color="error"
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No thresholds configured. Click "Add Threshold" to create one.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Add/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="md" fullWidth>
        <DialogTitle>
          {editingThreshold ? 'Edit Generation Threshold' : 'Add New Generation Threshold'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Threshold Name (Required)"
              value={formData.thresholdName}
              onChange={(e) => setFormData({ ...formData, thresholdName: e.target.value })}
              sx={{ mb: 2 }}
              required
            />

            <TextField
              fullWidth
              select
              label="Threshold Type (Required)"
              value={formData.thresholdType}
              onChange={(e) => setFormData({ ...formData, thresholdType: e.target.value as ThresholdType })}
              sx={{ mb: 2 }}
              required
            >
              <MenuItem value={ThresholdType.CLAIM_COUNT}>Claim Count (Trigger at X claims)</MenuItem>
              <MenuItem value={ThresholdType.AMOUNT}>Amount (Trigger at $X total)</MenuItem>
              <MenuItem value={ThresholdType.TIME}>Time-Based (Daily/Weekly/Monthly)</MenuItem>
              <MenuItem value={ThresholdType.HYBRID}>Hybrid (Multiple conditions)</MenuItem>
            </TextField>

            {(formData.thresholdType === ThresholdType.CLAIM_COUNT ||
              formData.thresholdType === ThresholdType.HYBRID) && (
              <TextField
                fullWidth
                label="Max Claims"
                type="number"
                value={formData.maxClaims || ''}
                onChange={(e) => setFormData({ ...formData, maxClaims: parseInt(e.target.value) || undefined })}
                sx={{ mb: 2 }}
                helperText="Trigger file generation when claim count reaches this value"
              />
            )}

            {(formData.thresholdType === ThresholdType.AMOUNT ||
              formData.thresholdType === ThresholdType.HYBRID) && (
              <TextField
                fullWidth
                label="Max Amount ($)"
                type="number"
                value={formData.maxAmount || ''}
                onChange={(e) => setFormData({ ...formData, maxAmount: parseFloat(e.target.value) || undefined })}
                sx={{ mb: 2 }}
                helperText="Trigger file generation when total amount reaches this value"
              />
            )}

            {(formData.thresholdType === ThresholdType.TIME ||
              formData.thresholdType === ThresholdType.HYBRID) && (
              <TextField
                fullWidth
                select
                label="Time Duration"
                value={formData.timeDuration || ''}
                onChange={(e) => setFormData({ ...formData, timeDuration: e.target.value as any })}
                sx={{ mb: 2 }}
              >
                <MenuItem value="DAILY">Daily</MenuItem>
                <MenuItem value="WEEKLY">Weekly</MenuItem>
                <MenuItem value="BIWEEKLY">Bi-Weekly</MenuItem>
                <MenuItem value="MONTHLY">Monthly</MenuItem>
              </TextField>
            )}

            <TextField
              fullWidth
              select
              label="Linked Bucketing Rule (Optional)"
              value={formData.linkedBucketingRule?.id || formData.linkedBucketingRule?.ruleId || ''}
              onChange={(e) => {
                const rule = bucketingRules?.find(r => (r.id || r.ruleId) === e.target.value);
                setFormData({ ...formData, linkedBucketingRule: rule });
              }}
              sx={{ mb: 2 }}
              helperText="Apply this threshold to a specific rule, or leave blank for all rules"
            >
              <MenuItem value="">All Rules</MenuItem>
              {bucketingRules?.map((rule) => (
                <MenuItem key={rule.id || rule.ruleId} value={rule.id || rule.ruleId}>
                  {rule.ruleName}
                </MenuItem>
              ))}
            </TextField>

            <FormControlLabel
              control={
                <Switch
                  checked={formData.isActive ?? true}
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
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? (
              <CircularProgress size={24} />
            ) : (
              editingThreshold ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete threshold "{editingThreshold?.thresholdName}"?
            This may affect automatic file generation. This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleteMutation.isPending}
          >
            {deleteMutation.isPending ? <CircularProgress size={24} /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ThresholdsConfig;
