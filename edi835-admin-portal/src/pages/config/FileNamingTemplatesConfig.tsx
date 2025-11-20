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
  TextField,
  Switch,
  FormControlLabel,
  Chip,
  MenuItem,
  Alert,
  Paper,
  TableSortLabel,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  Star as StarIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { configurationService } from '../../services/configurationService';
import { FileNamingTemplate, BucketingRule } from '../../types/models';
import { toast } from 'react-toastify';

type SortField = 'templateName' | 'templatePattern' | 'linkedRule' | 'caseConversion' | 'isDefault';
type SortOrder = 'asc' | 'desc';

const FileNamingTemplatesConfig: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<FileNamingTemplate | null>(null);
  const [formData, setFormData] = useState<Partial<FileNamingTemplate>>({
    templateName: '',
    templatePattern: '',
    isDefault: false,
    caseConversion: 'NONE',
  });
  const [sortField, setSortField] = useState<SortField>('templateName');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

  // Fetch templates
  const { data: templates, isLoading, refetch } = useQuery<FileNamingTemplate[]>({
    queryKey: ['fileNamingTemplates'],
    queryFn: configurationService.getAllTemplates,
  });

  // Fetch bucketing rules for dropdown
  const { data: bucketingRules } = useQuery<BucketingRule[]>({
    queryKey: ['bucketingRules'],
    queryFn: configurationService.getAllBucketingRules,
  });

  // Sort templates based on current sort field and order
  const sortedTemplates = useMemo(() => {
    if (!templates) return [];

    return [...templates].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'templateName':
          aValue = a.templateName.toLowerCase();
          bValue = b.templateName.toLowerCase();
          break;
        case 'templatePattern':
          aValue = a.templatePattern.toLowerCase();
          bValue = b.templatePattern.toLowerCase();
          break;
        case 'linkedRule':
          aValue = a.linkedBucketingRule?.ruleName?.toLowerCase() || '';
          bValue = b.linkedBucketingRule?.ruleName?.toLowerCase() || '';
          break;
        case 'caseConversion':
          aValue = (a.caseConversion || 'NONE').toLowerCase();
          bValue = (b.caseConversion || 'NONE').toLowerCase();
          break;
        case 'isDefault':
          aValue = a.isDefault ? 1 : 0;
          bValue = b.isDefault ? 1 : 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }, [templates, sortField, sortOrder]);

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
    mutationFn: (template: Partial<FileNamingTemplate>) =>
      configurationService.createTemplate(template),
    onSuccess: () => {
      toast.success('Template created successfully');
      queryClient.invalidateQueries({ queryKey: ['fileNamingTemplates'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to create template');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<FileNamingTemplate> }) =>
      configurationService.updateTemplate(id, data),
    onSuccess: () => {
      toast.success('Template updated successfully');
      queryClient.invalidateQueries({ queryKey: ['fileNamingTemplates'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to update template');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => configurationService.deleteTemplate(id),
    onSuccess: () => {
      toast.success('Template deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['fileNamingTemplates'] });
      setDeleteDialogOpen(false);
      setEditingTemplate(null);
    },
    onError: () => {
      toast.error('Failed to delete template');
    },
  });

  const handleOpenDialog = (template?: FileNamingTemplate) => {
    if (template) {
      setEditingTemplate(template);
      setFormData(template);
    } else {
      setEditingTemplate(null);
      setFormData({
        templateName: '',
        templatePattern: '',
        isDefault: false,
        caseConversion: 'NONE',
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingTemplate(null);
  };

  const handleSubmit = () => {
    if (!formData.templateName?.trim()) {
      toast.warning('Template name is required');
      return;
    }

    if (!formData.templatePattern?.trim()) {
      toast.warning('Template pattern is required');
      return;
    }

    if (editingTemplate) {
      updateMutation.mutate({ id: editingTemplate.templateId, data: formData });
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleDeleteClick = (template: FileNamingTemplate) => {
    setEditingTemplate(template);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (editingTemplate) {
      deleteMutation.mutate(editingTemplate.templateId);
    }
  };

  const getExampleOutput = (pattern: string): string => {
    const now = new Date();
    return pattern
      .replace('{payerId}', 'BCBS')
      .replace('{payerName}', 'BlueCross')
      .replace('{payeeId}', 'PHR001')
      .replace('{payeeName}', 'PharmacyChain')
      .replace('{date}', now.toISOString().split('T')[0])
      .replace('{date:yyyyMMdd}', '20231215')
      .replace('{date:MM-dd-yyyy}', '12-15-2023')
      .replace('{timestamp}', now.getTime().toString())
      .replace('{sequenceNumber}', '001')
      .replace('{sequenceNumber:6}', '000001')
      .replace('{bucketId}', 'abc123def456')
      + '.835';
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
            File Naming Templates Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Define naming patterns for generated EDI files
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
            Add Template
          </Button>
        </Box>
      </Box>

      {/* Info Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2" gutterBottom>
          <strong>Available Variables:</strong>
        </Typography>
        <Typography variant="body2" component="div">
          • <code>{'{payerId}'}</code> - Payer identifier <br />
          • <code>{'{payerName}'}</code> - Payer name <br />
          • <code>{'{payeeId}'}</code> - Payee identifier <br />
          • <code>{'{payeeName}'}</code> - Payee name <br />
          • <code>{'{date}'}</code> or <code>{'{date:format}'}</code> - Date with optional format <br />
          • <code>{'{timestamp}'}</code> - Unix timestamp <br />
          • <code>{'{sequenceNumber}'}</code> or <code>{'{sequenceNumber:6}'}</code> - Sequence with padding <br />
          • <code>{'{bucketId}'}</code> - Bucket identifier
        </Typography>
      </Alert>

      {/* Templates Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'templateName'}
                    direction={sortField === 'templateName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('templateName')}
                  >
                    <strong>Template Name</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'templatePattern'}
                    direction={sortField === 'templatePattern' ? sortOrder : 'asc'}
                    onClick={() => handleSort('templatePattern')}
                  >
                    <strong>Pattern</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell><strong>Example Output</strong></TableCell>
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
                    active={sortField === 'caseConversion'}
                    direction={sortField === 'caseConversion' ? sortOrder : 'asc'}
                    onClick={() => handleSort('caseConversion')}
                  >
                    <strong>Case Conversion</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'isDefault'}
                    direction={sortField === 'isDefault' ? sortOrder : 'asc'}
                    onClick={() => handleSort('isDefault')}
                  >
                    <strong>Default</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedTemplates.length > 0 ? (
                sortedTemplates.map((template) => (
                  <TableRow key={template.templateId} hover>
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="body2" fontWeight={600}>
                          {template.templateName}
                        </Typography>
                        {template.isDefault && (
                          <StarIcon fontSize="small" color="warning" />
                        )}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace" fontSize="0.85rem">
                        {template.templatePattern}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace" fontSize="0.85rem" color="primary">
                        {getExampleOutput(template.templatePattern)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {template.linkedBucketingRule?.ruleName || 'All Rules'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={template.caseConversion || 'NONE'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={template.isDefault ? 'Yes' : 'No'}
                        color={template.isDefault ? 'warning' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => handleOpenDialog(template)}
                          color="primary"
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteClick(template)}
                          color="error"
                          disabled={template.isDefault}
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
                      No templates configured. Click "Add Template" to create one.
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
          {editingTemplate ? 'Edit File Naming Template' : 'Add New File Naming Template'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Template Name (Required)"
              value={formData.templateName}
              onChange={(e) => setFormData({ ...formData, templateName: e.target.value })}
              sx={{ mb: 2 }}
              required
            />

            <TextField
              fullWidth
              label="Template Pattern (Required)"
              value={formData.templatePattern}
              onChange={(e) => setFormData({ ...formData, templatePattern: e.target.value })}
              sx={{ mb: 2 }}
              required
              placeholder="{payerId}_{payeeId}_{date:yyyyMMdd}_{sequenceNumber:6}"
              helperText="Use variables like {payerId}, {date}, {sequenceNumber:6}"
            />

            {formData.templatePattern && (
              <Paper sx={{ p: 2, mb: 2, bgcolor: 'grey.100' }}>
                <Typography variant="caption" color="textSecondary" gutterBottom>
                  Example Output:
                </Typography>
                <Typography variant="body2" fontFamily="monospace" color="primary">
                  {getExampleOutput(formData.templatePattern)}
                </Typography>
              </Paper>
            )}

            <TextField
              fullWidth
              select
              label="Linked Bucketing Rule (Optional)"
              value={formData.linkedBucketingRule?.ruleId || ''}
              onChange={(e) => {
                const rule = bucketingRules?.find(r => r.ruleId === e.target.value);
                setFormData({ ...formData, linkedBucketingRule: rule });
              }}
              sx={{ mb: 2 }}
              helperText="Apply this template to a specific rule, or leave blank for all rules"
            >
              <MenuItem value="">All Rules</MenuItem>
              {bucketingRules?.map((rule) => (
                <MenuItem key={rule.ruleId} value={rule.ruleId}>
                  {rule.ruleName}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              fullWidth
              select
              label="Case Conversion"
              value={formData.caseConversion || 'NONE'}
              onChange={(e) => setFormData({ ...formData, caseConversion: e.target.value as any })}
              sx={{ mb: 2 }}
            >
              <MenuItem value="NONE">None (Keep as is)</MenuItem>
              <MenuItem value="UPPERCASE">UPPERCASE</MenuItem>
              <MenuItem value="LOWERCASE">lowercase</MenuItem>
              <MenuItem value="CAPITALIZE">Capitalize</MenuItem>
            </TextField>

            <FormControlLabel
              control={
                <Switch
                  checked={formData.isDefault || false}
                  onChange={(e) => setFormData({ ...formData, isDefault: e.target.checked })}
                />
              }
              label="Set as Default Template"
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
              editingTemplate ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete template "{editingTemplate?.templateName}"?
            This may affect file naming. This action cannot be undone.
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

export default FileNamingTemplatesConfig;
