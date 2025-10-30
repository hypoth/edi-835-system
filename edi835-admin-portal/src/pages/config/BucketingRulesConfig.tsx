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
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { configurationService } from '../../services/configurationService';
import { BucketingRule, RuleType } from '../../types/models';
import { toast } from 'react-toastify';

const BucketingRulesConfig: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<BucketingRule | null>(null);
  const [formData, setFormData] = useState<Partial<BucketingRule>>({
    ruleName: '',
    ruleType: RuleType.PAYER_PAYEE,
    priority: 1,
    groupingExpression: '',
    isActive: true,
  });

  // Fetch bucketing rules
  const { data: rules, isLoading, refetch } = useQuery<BucketingRule[]>({
    queryKey: ['bucketingRules'],
    queryFn: configurationService.getAllBucketingRules,
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (rule: Partial<BucketingRule>) => configurationService.createBucketingRule(rule),
    onSuccess: () => {
      toast.success('Bucketing rule created successfully');
      queryClient.invalidateQueries({ queryKey: ['bucketingRules'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to create bucketing rule');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<BucketingRule> }) =>
      configurationService.updateBucketingRule(id, data),
    onSuccess: () => {
      toast.success('Bucketing rule updated successfully');
      queryClient.invalidateQueries({ queryKey: ['bucketingRules'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to update bucketing rule');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => configurationService.deleteBucketingRule(id),
    onSuccess: () => {
      toast.success('Bucketing rule deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['bucketingRules'] });
      setDeleteDialogOpen(false);
      setEditingRule(null);
    },
    onError: () => {
      toast.error('Failed to delete bucketing rule');
    },
  });

  const handleOpenDialog = (rule?: BucketingRule) => {
    if (rule) {
      setEditingRule(rule);
      setFormData(rule);
    } else {
      setEditingRule(null);
      setFormData({
        ruleName: '',
        ruleType: RuleType.PAYER_PAYEE,
        priority: rules ? rules.length + 1 : 1,
        groupingExpression: '',
        isActive: true,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingRule(null);
  };

  const handleSubmit = () => {
    if (!formData.ruleName?.trim()) {
      toast.warning('Rule name is required');
      return;
    }

    if (formData.ruleType === RuleType.CUSTOM && !formData.groupingExpression?.trim()) {
      toast.warning('Grouping expression is required for custom rules');
      return;
    }

    if (editingRule) {
      const ruleId = editingRule.id || editingRule.ruleId;
      if (!ruleId) {
        toast.error('Invalid rule ID');
        return;
      }
      updateMutation.mutate({ id: ruleId, data: formData });
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleDeleteClick = (rule: BucketingRule) => {
    setEditingRule(rule);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (editingRule) {
      const ruleId = editingRule.id || editingRule.ruleId;
      if (!ruleId) {
        toast.error('Invalid rule ID');
        return;
      }
      deleteMutation.mutate(ruleId);
    }
  };

  const getRuleTypeLabel = (type: RuleType): string => {
    switch (type) {
      case RuleType.PAYER_PAYEE:
        return 'Payer + Payee';
      case RuleType.BIN_PCN:
        return 'BIN + PCN';
      case RuleType.CUSTOM:
        return 'Custom';
      default:
        return type;
    }
  };

  const getRuleTypeColor = (type: RuleType): 'primary' | 'secondary' | 'default' => {
    switch (type) {
      case RuleType.PAYER_PAYEE:
        return 'primary';
      case RuleType.BIN_PCN:
        return 'secondary';
      case RuleType.CUSTOM:
        return 'default';
      default:
        return 'default';
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
            Bucketing Rules Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Define rules for grouping claims into EDI file buckets
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
            Add Rule
          </Button>
        </Box>
      </Box>

      {/* Rules Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Priority</strong></TableCell>
                <TableCell><strong>Rule Name</strong></TableCell>
                <TableCell><strong>Rule Type</strong></TableCell>
                <TableCell><strong>Grouping Expression</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell><strong>Created</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rules && rules.length > 0 ? (
                rules
                  .sort((a, b) => a.priority - b.priority)
                  .map((rule) => (
                    <TableRow key={rule.id || rule.ruleId} hover>
                      <TableCell>
                        <Chip label={rule.priority} color="primary" size="small" />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {rule.ruleName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={getRuleTypeLabel(rule.ruleType)}
                          color={getRuleTypeColor(rule.ruleType)}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        {rule.groupingExpression ? (
                          <Typography variant="body2" fontFamily="monospace" fontSize="0.85rem">
                            {rule.groupingExpression}
                          </Typography>
                        ) : (
                          <Typography variant="body2" color="textSecondary">
                            Default grouping
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={rule.isActive ? 'Active' : 'Inactive'}
                          color={rule.isActive ? 'success' : 'default'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {new Date(rule.createdAt).toLocaleDateString()}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => handleOpenDialog(rule)}
                            color="primary"
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton
                            size="small"
                            onClick={() => handleDeleteClick(rule)}
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
                  <TableCell colSpan={7} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No bucketing rules configured. Click "Add Rule" to create one.
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
        <DialogTitle>{editingRule ? 'Edit Bucketing Rule' : 'Add New Bucketing Rule'}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Rule Name (Required)"
              value={formData.ruleName}
              onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
              sx={{ mb: 2 }}
              required
            />

            <TextField
              fullWidth
              select
              label="Rule Type (Required)"
              value={formData.ruleType}
              onChange={(e) => setFormData({ ...formData, ruleType: e.target.value as RuleType })}
              sx={{ mb: 2 }}
              required
            >
              <MenuItem value={RuleType.PAYER_PAYEE}>Payer + Payee (Default)</MenuItem>
              <MenuItem value={RuleType.BIN_PCN}>BIN + PCN (Pharmacy)</MenuItem>
              <MenuItem value={RuleType.CUSTOM}>Custom Expression</MenuItem>
            </TextField>

            <TextField
              fullWidth
              label="Priority (Lower = Higher Priority)"
              type="number"
              value={formData.priority}
              onChange={(e) => setFormData({ ...formData, priority: parseInt(e.target.value) || 1 })}
              sx={{ mb: 2 }}
              helperText="Rules are evaluated in priority order (1 = highest priority)"
            />

            {formData.ruleType === RuleType.CUSTOM && (
              <TextField
                fullWidth
                multiline
                rows={3}
                label="Grouping Expression (Required for Custom)"
                value={formData.groupingExpression}
                onChange={(e) => setFormData({ ...formData, groupingExpression: e.target.value })}
                sx={{ mb: 2 }}
                helperText="Define custom grouping logic (e.g., payerId + payeeId + binNumber)"
                required
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
              editingRule ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete rule "{editingRule?.ruleName}"?
            This may affect bucket creation. This action cannot be undone.
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

export default BucketingRulesConfig;
