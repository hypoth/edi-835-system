import React, { useState, useMemo } from 'react';
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
  Chip,
  Alert,
  Tabs,
  Tab,
  Checkbox,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  TableSortLabel,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  MoreVert as MoreIcon,
  PowerSettingsNew as ToggleIcon,
  Science as TestIcon,
  Assessment as AnalyticsIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { configurationService } from '../../services/configurationService';
import { bucketService } from '../../services/bucketService';
import { GenerationThreshold, ThresholdType, BucketingRule, Bucket } from '../../types/models';
import { toast } from 'react-toastify';

// Import new components
import ThresholdTypeSelector from '../../components/thresholds/ThresholdTypeSelector';
import ThresholdTemplates, { ThresholdTemplate } from '../../components/thresholds/ThresholdTemplates';
import ThresholdForm from '../../components/thresholds/ThresholdForm';
import ThresholdValidator from '../../components/thresholds/ThresholdValidator';
import ThresholdPreview from '../../components/thresholds/ThresholdPreview';
import ThresholdTesting from '../../components/thresholds/ThresholdTesting';
import ThresholdAnalytics, { generateMockAnalytics } from '../../components/thresholds/ThresholdAnalytics';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`threshold-tabpanel-${index}`}
      aria-labelledby={`threshold-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

type SortField = 'thresholdName' | 'thresholdType' | 'maxClaims' | 'maxAmount' | 'timeDuration' | 'linkedRule' | 'isActive';
type SortOrder = 'asc' | 'desc';

const ThresholdsConfigEnhanced: React.FC = () => {
  const queryClient = useQueryClient();
  const [currentTab, setCurrentTab] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [testDialogOpen, setTestDialogOpen] = useState(false);
  const [analyticsDialogOpen, setAnalyticsDialogOpen] = useState(false);
  const [editingThreshold, setEditingThreshold] = useState<GenerationThreshold | null>(null);
  const [selectedThresholds, setSelectedThresholds] = useState<string[]>([]);
  const [bulkMenuAnchor, setBulkMenuAnchor] = useState<null | HTMLElement>(null);
  const [dialogStep, setDialogStep] = useState(0); // 0: Type, 1: Form, 2: Preview, 3: Test
  const [sortField, setSortField] = useState<SortField>('thresholdName');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

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

  // Fetch bucketing rules
  const { data: bucketingRules } = useQuery<BucketingRule[]>({
    queryKey: ['bucketingRules'],
    queryFn: configurationService.getAllBucketingRules,
  });

  // Fetch active buckets for preview
  const { data: activeBuckets } = useQuery<Bucket[]>({
    queryKey: ['activeBuckets'],
    queryFn: bucketService.getActiveBuckets,
  });

  // Sort thresholds based on current sort field and order
  const sortedThresholds = useMemo(() => {
    if (!thresholds) return [];

    return [...thresholds].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'thresholdName':
          aValue = a.thresholdName.toLowerCase();
          bValue = b.thresholdName.toLowerCase();
          break;
        case 'thresholdType':
          aValue = a.thresholdType;
          bValue = b.thresholdType;
          break;
        case 'maxClaims':
          aValue = a.maxClaims || 0;
          bValue = b.maxClaims || 0;
          break;
        case 'maxAmount':
          aValue = a.maxAmount || 0;
          bValue = b.maxAmount || 0;
          break;
        case 'timeDuration':
          aValue = a.timeDuration?.toLowerCase() || '';
          bValue = b.timeDuration?.toLowerCase() || '';
          break;
        case 'linkedRule':
          aValue = a.linkedBucketingRule?.ruleName?.toLowerCase() || '';
          bValue = b.linkedBucketingRule?.ruleName?.toLowerCase() || '';
          break;
        case 'isActive':
          aValue = a.isActive ? 1 : 0;
          bValue = b.isActive ? 1 : 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }, [thresholds, sortField, sortOrder]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      // Toggle sort order if same field
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      // Set new field with ascending order
      setSortField(field);
      setSortOrder('asc');
    }
  };

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

  // Bulk update status mutation
  const bulkUpdateStatusMutation = useMutation({
    mutationFn: ({ thresholdIds, isActive }: { thresholdIds: string[]; isActive: boolean }) =>
      configurationService.bulkUpdateThresholdStatus(thresholdIds, isActive),
    onSuccess: () => {
      toast.success('Thresholds updated successfully');
      queryClient.invalidateQueries({ queryKey: ['thresholds'] });
      setSelectedThresholds([]);
      setBulkMenuAnchor(null);
    },
    onError: () => {
      toast.error('Failed to update thresholds');
    },
  });

  const handleOpenDialog = (threshold?: GenerationThreshold) => {
    if (threshold) {
      setEditingThreshold(threshold);
      setFormData(threshold);
      setDialogStep(1); // Skip type selection when editing
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
      setDialogStep(0); // Start with type selection
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingThreshold(null);
    setDialogStep(0);
  };

  const handleTemplateSelect = (template: ThresholdTemplate) => {
    setFormData({
      thresholdName: template.name,
      thresholdType: template.thresholdType,
      maxClaims: template.maxClaims,
      maxAmount: template.maxAmount,
      timeDuration: template.timeDuration,
      isActive: true,
    });
    setDialogStep(1); // Move to form
  };

  const handleSubmit = () => {
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

  const handleTestClick = (threshold: GenerationThreshold) => {
    setEditingThreshold(threshold);
    setFormData(threshold);
    setTestDialogOpen(true);
  };

  const handleAnalyticsClick = (threshold: GenerationThreshold) => {
    setEditingThreshold(threshold);
    setAnalyticsDialogOpen(true);
  };

  const handleSelectThreshold = (thresholdId: string) => {
    setSelectedThresholds((prev) =>
      prev.includes(thresholdId)
        ? prev.filter((id) => id !== thresholdId)
        : [...prev, thresholdId]
    );
  };

  const handleSelectAll = () => {
    if (selectedThresholds.length === sortedThresholds.length) {
      setSelectedThresholds([]);
    } else {
      setSelectedThresholds(sortedThresholds.map((t) => t.thresholdId));
    }
  };

  const handleBulkActivate = () => {
    bulkUpdateStatusMutation.mutate({ thresholdIds: selectedThresholds, isActive: true });
  };

  const handleBulkDeactivate = () => {
    bulkUpdateStatusMutation.mutate({ thresholdIds: selectedThresholds, isActive: false });
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

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={currentTab} onChange={(_, newValue) => setCurrentTab(newValue)}>
          <Tab label="Active Thresholds" />
          <Tab label="Templates" />
        </Tabs>
      </Box>

      {/* Tab: Active Thresholds */}
      <TabPanel value={currentTab} index={0}>
        {/* Bulk Actions */}
        {selectedThresholds.length > 0 && (
          <Alert severity="info" sx={{ mb: 2 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Typography variant="body2">
                {selectedThresholds.length} threshold(s) selected
              </Typography>
              <Box>
                <Button
                  size="small"
                  onClick={(e) => setBulkMenuAnchor(e.currentTarget)}
                  endIcon={<MoreIcon />}
                >
                  Bulk Actions
                </Button>
                <Menu
                  anchorEl={bulkMenuAnchor}
                  open={Boolean(bulkMenuAnchor)}
                  onClose={() => setBulkMenuAnchor(null)}
                >
                  <MenuItem onClick={handleBulkActivate}>
                    <ListItemIcon>
                      <ToggleIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Activate Selected</ListItemText>
                  </MenuItem>
                  <MenuItem onClick={handleBulkDeactivate}>
                    <ListItemIcon>
                      <ToggleIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Deactivate Selected</ListItemText>
                  </MenuItem>
                </Menu>
              </Box>
            </Box>
          </Alert>
        )}

        {/* Thresholds Table */}
        <Card>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={sortedThresholds.length > 0 && selectedThresholds.length === sortedThresholds.length}
                      indeterminate={
                        selectedThresholds.length > 0 &&
                        selectedThresholds.length < sortedThresholds.length
                      }
                      onChange={handleSelectAll}
                    />
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'thresholdName'}
                      direction={sortField === 'thresholdName' ? sortOrder : 'asc'}
                      onClick={() => handleSort('thresholdName')}
                    >
                      <strong>Threshold Name</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'thresholdType'}
                      direction={sortField === 'thresholdType' ? sortOrder : 'asc'}
                      onClick={() => handleSort('thresholdType')}
                    >
                      <strong>Type</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'maxClaims'}
                      direction={sortField === 'maxClaims' ? sortOrder : 'asc'}
                      onClick={() => handleSort('maxClaims')}
                    >
                      <strong>Max Claims</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'maxAmount'}
                      direction={sortField === 'maxAmount' ? sortOrder : 'asc'}
                      onClick={() => handleSort('maxAmount')}
                    >
                      <strong>Max Amount</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'timeDuration'}
                      direction={sortField === 'timeDuration' ? sortOrder : 'asc'}
                      onClick={() => handleSort('timeDuration')}
                    >
                      <strong>Time Duration</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'linkedRule'}
                      direction={sortField === 'linkedRule' ? sortOrder : 'asc'}
                      onClick={() => handleSort('linkedRule')}
                    >
                      <strong>Linked Rule</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'isActive'}
                      direction={sortField === 'isActive' ? sortOrder : 'asc'}
                      onClick={() => handleSort('isActive')}
                    >
                      <strong>Status</strong>
                    </TableSortLabel>
                  </TableCell>
                  <TableCell align="center"><strong>Actions</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedThresholds.length > 0 ? (
                  sortedThresholds.map((threshold) => (
                    <TableRow key={threshold.thresholdId} hover>
                      <TableCell padding="checkbox">
                        <Checkbox
                          checked={selectedThresholds.includes(threshold.thresholdId)}
                          onChange={() => handleSelectThreshold(threshold.thresholdId)}
                        />
                      </TableCell>
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
                        <Tooltip title="Test">
                          <IconButton
                            size="small"
                            onClick={() => handleTestClick(threshold)}
                            color="info"
                          >
                            <TestIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Analytics">
                          <IconButton
                            size="small"
                            onClick={() => handleAnalyticsClick(threshold)}
                            color="success"
                          >
                            <AnalyticsIcon fontSize="small" />
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
                    <TableCell colSpan={9} align="center">
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
      </TabPanel>

      {/* Tab: Templates */}
      <TabPanel value={currentTab} index={1}>
        <ThresholdTemplates
          onSelectTemplate={(template) => {
            handleTemplateSelect(template);
            setDialogOpen(true);
          }}
        />
      </TabPanel>

      {/* Add/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="lg" fullWidth>
        <DialogTitle>
          {editingThreshold ? 'Edit Generation Threshold' : 'Add New Generation Threshold'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            {/* Multi-step form */}
            {dialogStep === 0 && !editingThreshold && (
              <ThresholdTypeSelector
                value={formData.thresholdType || ThresholdType.CLAIM_COUNT}
                onChange={(type) => setFormData({ ...formData, thresholdType: type })}
              />
            )}

            {dialogStep === 1 && (
              <Box>
                <ThresholdForm
                  formData={formData}
                  onChange={setFormData}
                  bucketingRules={bucketingRules}
                />
                <Box mt={3}>
                  <ThresholdValidator
                    threshold={formData}
                    existingThresholds={thresholds}
                    showValidation={true}
                  />
                </Box>
              </Box>
            )}

            {dialogStep === 2 && (
              <ThresholdPreview
                threshold={formData}
                buckets={activeBuckets}
              />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          {dialogStep > 0 && !editingThreshold && (
            <Button onClick={() => setDialogStep(dialogStep - 1)}>
              Back
            </Button>
          )}
          <Button onClick={handleCloseDialog}>Cancel</Button>
          {dialogStep === 0 && !editingThreshold && (
            <Button
              onClick={() => setDialogStep(1)}
              variant="contained"
            >
              Next
            </Button>
          )}
          {dialogStep === 1 && !editingThreshold && (
            <Button
              onClick={() => setDialogStep(2)}
              variant="outlined"
            >
              Preview
            </Button>
          )}
          {(dialogStep === 1 || dialogStep === 2 || editingThreshold) && (
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
          )}
        </DialogActions>
      </Dialog>

      {/* Test Dialog */}
      <Dialog open={testDialogOpen} onClose={() => setTestDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Test Threshold Configuration</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            {editingThreshold && (
              <ThresholdTesting threshold={formData} />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Analytics Dialog */}
      <Dialog open={analyticsDialogOpen} onClose={() => setAnalyticsDialogOpen(false)} maxWidth="lg" fullWidth>
        <DialogTitle>Threshold Analytics</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            {editingThreshold && (
              <ThresholdAnalytics
                threshold={editingThreshold}
                analyticsData={generateMockAnalytics(editingThreshold.thresholdId)}
              />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAnalyticsDialogOpen(false)}>Close</Button>
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

export default ThresholdsConfigEnhanced;
