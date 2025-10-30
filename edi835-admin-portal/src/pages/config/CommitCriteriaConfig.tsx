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
import { CommitCriteria, CommitMode, BucketingRule } from '../../types/models';
import { toast } from 'react-toastify';

const CommitCriteriaConfig: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingCriteria, setEditingCriteria] = useState<CommitCriteria | null>(null);
  const [formData, setFormData] = useState<Partial<CommitCriteria>>({
    criteriaName: '',
    commitMode: CommitMode.AUTO,
    approvalClaimCountThreshold: undefined,
    approvalAmountThreshold: undefined,
    approvalRoles: '',
    isActive: true,
  });

  // Fetch commit criteria
  const { data: criteriaList, isLoading, refetch } = useQuery<CommitCriteria[]>({
    queryKey: ['commitCriteria'],
    queryFn: configurationService.getAllCommitCriteria,
  });

  // Fetch bucketing rules for dropdown
  const { data: bucketingRules } = useQuery<BucketingRule[]>({
    queryKey: ['bucketingRules'],
    queryFn: configurationService.getAllBucketingRules,
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (criteria: Partial<CommitCriteria>) =>
      configurationService.createCommitCriteria(criteria),
    onSuccess: () => {
      toast.success('Commit criteria created successfully');
      queryClient.invalidateQueries({ queryKey: ['commitCriteria'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to create commit criteria');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<CommitCriteria> }) =>
      configurationService.updateCommitCriteria(id, data),
    onSuccess: () => {
      toast.success('Commit criteria updated successfully');
      queryClient.invalidateQueries({ queryKey: ['commitCriteria'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to update commit criteria');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => configurationService.deleteCommitCriteria(id),
    onSuccess: () => {
      toast.success('Commit criteria deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['commitCriteria'] });
      setDeleteDialogOpen(false);
      setEditingCriteria(null);
    },
    onError: () => {
      toast.error('Failed to delete commit criteria');
    },
  });

  const handleOpenDialog = (criteria?: CommitCriteria) => {
    if (criteria) {
      setEditingCriteria(criteria);
      // Map backend DTO fields to form data
      setFormData({
        ...criteria,
        // Handle linked bucketing rule - create object if IDs exist
        linkedBucketingRule: criteria.linkedBucketingRuleId ? {
          id: criteria.linkedBucketingRuleId,
          ruleId: criteria.linkedBucketingRuleId, // For backward compatibility
          ruleName: criteria.linkedBucketingRuleName || '',
          ruleType: 'PAYER_PAYEE' as any, // Default, will be ignored
          priority: 0,
          isActive: true,
          createdAt: '',
          updatedAt: '',
        } : undefined,
        // Handle approval roles - convert array to string if needed
        approvalRoles: criteria.approvalRequiredRoles?.join(', ') || criteria.approvalRoles || '',
      });
    } else {
      setEditingCriteria(null);
      setFormData({
        criteriaName: '',
        commitMode: CommitMode.AUTO,
        approvalClaimCountThreshold: undefined,
        approvalAmountThreshold: undefined,
        approvalRoles: '',
        isActive: true,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingCriteria(null);
  };

  const handleSubmit = () => {
    if (!formData.criteriaName?.trim()) {
      toast.warning('Criteria name is required');
      return;
    }

    if (formData.commitMode === CommitMode.MANUAL && !formData.approvalRoles?.trim()) {
      toast.warning('Approval roles are required for manual mode');
      return;
    }

    if (
      formData.commitMode === CommitMode.HYBRID &&
      (!formData.approvalClaimCountThreshold || !formData.approvalAmountThreshold)
    ) {
      toast.warning('Both claim count and amount thresholds are required for hybrid mode');
      return;
    }

    // Prepare data for backend - map to DTO structure
    const dataToSend = {
      ...formData,
      // Map linked bucketing rule to just the ID
      linkedBucketingRuleId: formData.linkedBucketingRule?.id || formData.linkedBucketingRule?.ruleId || null,
      // Remove the full object as backend expects just ID
      linkedBucketingRule: undefined,
      // Convert approval roles string to array if needed
      approvalRequiredRoles: formData.approvalRoles?.split(',').map(r => r.trim()).filter(r => r) || [],
    };

    if (editingCriteria) {
      // Use 'id' field from backend DTO, not 'criteriaId'
      const criteriaId = editingCriteria.id || editingCriteria.criteriaId;
      if (!criteriaId) {
        toast.error('Invalid criteria ID');
        return;
      }
      updateMutation.mutate({ id: criteriaId, data: dataToSend });
    } else {
      createMutation.mutate(dataToSend);
    }
  };

  const handleDeleteClick = (criteria: CommitCriteria) => {
    setEditingCriteria(criteria);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (editingCriteria) {
      const criteriaId = editingCriteria.id || editingCriteria.criteriaId;
      if (!criteriaId) {
        toast.error('Invalid criteria ID');
        return;
      }
      deleteMutation.mutate(criteriaId);
    }
  };

  const getCommitModeLabel = (mode: CommitMode): string => {
    switch (mode) {
      case CommitMode.AUTO:
        return 'Auto';
      case CommitMode.MANUAL:
        return 'Manual';
      case CommitMode.HYBRID:
        return 'Hybrid';
      default:
        return mode;
    }
  };

  const getCommitModeColor = (mode: CommitMode): 'success' | 'warning' | 'info' => {
    switch (mode) {
      case CommitMode.AUTO:
        return 'success';
      case CommitMode.MANUAL:
        return 'warning';
      case CommitMode.HYBRID:
        return 'info';
      default:
        return 'info';
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
            Commit Criteria Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Define approval workflows and automatic file generation rules
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
            Add Criteria
          </Button>
        </Box>
      </Box>

      {/* Info Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          <strong>AUTO:</strong> Files generate automatically when claim count or amount thresholds are met. <br />
          <strong>MANUAL:</strong> All files require approval before generation (no thresholds needed). <br />
          <strong>HYBRID:</strong> Small buckets auto-generate, buckets exceeding thresholds require approval.
        </Typography>
      </Alert>

      {/* Commit Criteria Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Criteria Name</strong></TableCell>
                <TableCell><strong>Commit Mode</strong></TableCell>
                <TableCell><strong>Linked Rule</strong></TableCell>
                <TableCell><strong>Claim Threshold</strong></TableCell>
                <TableCell><strong>Amount Threshold</strong></TableCell>
                <TableCell><strong>Approval Roles</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {criteriaList && criteriaList.length > 0 ? (
                criteriaList.map((criteria) => (
                  <TableRow key={criteria.id || criteria.criteriaId} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {criteria.criteriaName || 'Unnamed Criteria'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={getCommitModeLabel(criteria.commitMode)}
                        color={getCommitModeColor(criteria.commitMode)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {criteria.linkedBucketingRuleName || criteria.linkedBucketingRule?.ruleName || 'All Rules'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {criteria.approvalClaimCountThreshold || criteria.manualApprovalThreshold
                          ? `${criteria.approvalClaimCountThreshold || criteria.manualApprovalThreshold} claims`
                          : 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {criteria.approvalAmountThreshold
                          ? `$${criteria.approvalAmountThreshold.toLocaleString()}`
                          : 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {criteria.approvalRequiredRoles && criteria.approvalRequiredRoles.length > 0 ? (
                        <Box>
                          {criteria.approvalRequiredRoles.map((role, idx) => (
                            <Chip
                              key={idx}
                              label={role.trim()}
                              size="small"
                              sx={{ mr: 0.5, mb: 0.5 }}
                            />
                          ))}
                        </Box>
                      ) : criteria.approvalRoles ? (
                        <Box>
                          {criteria.approvalRoles.split(',').map((role, idx) => (
                            <Chip
                              key={idx}
                              label={role.trim()}
                              size="small"
                              sx={{ mr: 0.5, mb: 0.5 }}
                            />
                          ))}
                        </Box>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          N/A
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={criteria.isActive ? 'Active' : 'Inactive'}
                        color={criteria.isActive ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => handleOpenDialog(criteria)}
                          color="primary"
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteClick(criteria)}
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
                      No commit criteria configured. Click "Add Criteria" to create one.
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
          {editingCriteria ? 'Edit Commit Criteria' : 'Add New Commit Criteria'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Criteria Name (Required)"
              value={formData.criteriaName}
              onChange={(e) => setFormData({ ...formData, criteriaName: e.target.value })}
              sx={{ mb: 2 }}
              required
              placeholder="e.g., BCBS Auto-Commit, UHC Manual Approval"
              helperText="Enter a descriptive name for this commit criteria"
            />

            <TextField
              fullWidth
              select
              label="Commit Mode (Required)"
              value={formData.commitMode}
              onChange={(e) => setFormData({ ...formData, commitMode: e.target.value as CommitMode })}
              sx={{ mb: 2 }}
              required
              helperText={
                formData.commitMode === CommitMode.AUTO
                  ? 'Files generate automatically when thresholds are met'
                  : formData.commitMode === CommitMode.MANUAL
                  ? 'All files require manual approval (no automatic generation)'
                  : 'Buckets exceeding thresholds require approval, others auto-generate'
              }
            >
              <MenuItem value={CommitMode.AUTO}>Auto - Generate when thresholds met</MenuItem>
              <MenuItem value={CommitMode.MANUAL}>Manual - Always require approval</MenuItem>
              <MenuItem value={CommitMode.HYBRID}>Hybrid - Conditional approval based on size</MenuItem>
            </TextField>

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
              helperText="Apply this criteria to a specific rule, or leave blank for all rules"
            >
              <MenuItem value="">All Rules</MenuItem>
              {bucketingRules?.map((rule) => (
                <MenuItem key={rule.id || rule.ruleId} value={rule.id || rule.ruleId}>
                  {rule.ruleName}
                </MenuItem>
              ))}
            </TextField>

            {formData.commitMode === CommitMode.AUTO && (
              <>
                <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
                  Auto-Commit Thresholds (Files generate automatically when these are met)
                </Typography>
                <TextField
                  fullWidth
                  label="Claim Count Threshold"
                  type="number"
                  value={formData.approvalClaimCountThreshold || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      approvalClaimCountThreshold: parseInt(e.target.value) || undefined,
                    })
                  }
                  sx={{ mb: 2 }}
                  helperText="Generate file when bucket reaches this many claims"
                />
                <TextField
                  fullWidth
                  label="Amount Threshold ($)"
                  type="number"
                  value={formData.approvalAmountThreshold || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      approvalAmountThreshold: parseFloat(e.target.value) || undefined,
                    })
                  }
                  sx={{ mb: 2 }}
                  helperText="Generate file when bucket total reaches this amount"
                />
              </>
            )}

            {formData.commitMode === CommitMode.HYBRID && (
              <>
                <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
                  Hybrid Mode Thresholds (Buckets exceeding these require approval)
                </Typography>
                <TextField
                  fullWidth
                  label="Approval Claim Count Threshold"
                  type="number"
                  value={formData.approvalClaimCountThreshold || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      approvalClaimCountThreshold: parseInt(e.target.value) || undefined,
                    })
                  }
                  sx={{ mb: 2 }}
                  helperText="Buckets with more claims than this require approval"
                />
                <TextField
                  fullWidth
                  label="Approval Amount Threshold ($)"
                  type="number"
                  value={formData.approvalAmountThreshold || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      approvalAmountThreshold: parseFloat(e.target.value) || undefined,
                    })
                  }
                  sx={{ mb: 2 }}
                  helperText="Buckets with more amount than this require approval"
                />
              </>
            )}

            {(formData.commitMode === CommitMode.MANUAL || formData.commitMode === CommitMode.HYBRID) && (
              <TextField
                fullWidth
                label="Approval Roles (Comma-separated)"
                value={formData.approvalRoles}
                onChange={(e) => setFormData({ ...formData, approvalRoles: e.target.value })}
                sx={{ mb: 2 }}
                placeholder="e.g., ADMIN, FINANCE_MANAGER, SUPERVISOR"
                helperText="Users with these roles can approve files"
              />
            )}

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
              editingCriteria ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete this commit criteria?
            This may affect approval workflows. This action cannot be undone.
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

export default CommitCriteriaConfig;
