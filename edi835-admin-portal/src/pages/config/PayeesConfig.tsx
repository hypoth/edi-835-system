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
  TableSortLabel,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { configurationService } from '../../services/configurationService';
import { Payee } from '../../types/models';
import { toast } from 'react-toastify';

type SortField = 'payeeId' | 'payeeName' | 'npi' | 'taxId' | 'isActive' | 'requiresSpecialHandling';
type SortOrder = 'asc' | 'desc';

const PayeesConfig: React.FC = () => {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingPayee, setEditingPayee] = useState<Payee | null>(null);
  const [formData, setFormData] = useState<Partial<Payee>>({
    payeeId: '',
    payeeName: '',
    npi: '',
    taxId: '',
    contactEmail: '',
    contactPhone: '',
    requiresSpecialHandling: false,
    isActive: true,
  });
  const [sortField, setSortField] = useState<SortField>('payeeName');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

  // Fetch payees
  const { data: payees, isLoading, refetch } = useQuery<Payee[]>({
    queryKey: ['payees'],
    queryFn: configurationService.getAllPayees,
  });

  // Sort payees based on current sort field and order
  const sortedPayees = useMemo(() => {
    if (!payees) return [];

    return [...payees].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'payeeId':
          aValue = a.payeeId.toLowerCase();
          bValue = b.payeeId.toLowerCase();
          break;
        case 'payeeName':
          aValue = a.payeeName.toLowerCase();
          bValue = b.payeeName.toLowerCase();
          break;
        case 'npi':
          aValue = a.npi?.toLowerCase() || '';
          bValue = b.npi?.toLowerCase() || '';
          break;
        case 'taxId':
          aValue = a.taxId?.toLowerCase() || '';
          bValue = b.taxId?.toLowerCase() || '';
          break;
        case 'isActive':
          aValue = a.isActive ? 1 : 0;
          bValue = b.isActive ? 1 : 0;
          break;
        case 'requiresSpecialHandling':
          aValue = a.requiresSpecialHandling ? 1 : 0;
          bValue = b.requiresSpecialHandling ? 1 : 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }, [payees, sortField, sortOrder]);

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
    mutationFn: (payee: Partial<Payee>) => configurationService.createPayee(payee),
    onSuccess: () => {
      toast.success('Payee created successfully');
      queryClient.invalidateQueries({ queryKey: ['payees'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to create payee');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Payee> }) =>
      configurationService.updatePayee(id, data),
    onSuccess: () => {
      toast.success('Payee updated successfully');
      queryClient.invalidateQueries({ queryKey: ['payees'] });
      handleCloseDialog();
    },
    onError: () => {
      toast.error('Failed to update payee');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => configurationService.deletePayee(id),
    onSuccess: () => {
      toast.success('Payee deleted successfully');
      queryClient.invalidateQueries({ queryKey: ['payees'] });
      setDeleteDialogOpen(false);
      setEditingPayee(null);
    },
    onError: () => {
      toast.error('Failed to delete payee');
    },
  });

  const handleOpenDialog = (payee?: Payee) => {
    if (payee) {
      setEditingPayee(payee);
      setFormData(payee);
    } else {
      setEditingPayee(null);
      setFormData({
        payeeId: '',
        payeeName: '',
        npi: '',
        taxId: '',
        contactEmail: '',
        contactPhone: '',
        requiresSpecialHandling: false,
        isActive: true,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingPayee(null);
  };

  const handleSubmit = () => {
    if (!formData.payeeId?.trim() || !formData.payeeName?.trim()) {
      toast.warning('Payee ID and Name are required');
      return;
    }

    if (editingPayee) {
      updateMutation.mutate({ id: editingPayee.id, data: formData });
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleDeleteClick = (payee: Payee) => {
    setEditingPayee(payee);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (editingPayee) {
      deleteMutation.mutate(editingPayee.id);
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
            Payees Configuration
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Manage healthcare providers and pharmacies receiving remittance files
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
            Add Payee
          </Button>
        </Box>
      </Box>

      {/* Payees Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'payeeId'}
                    direction={sortField === 'payeeId' ? sortOrder : 'asc'}
                    onClick={() => handleSort('payeeId')}
                  >
                    <strong>Payee ID</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'payeeName'}
                    direction={sortField === 'payeeName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('payeeName')}
                  >
                    <strong>Payee Name</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'npi'}
                    direction={sortField === 'npi' ? sortOrder : 'asc'}
                    onClick={() => handleSort('npi')}
                  >
                    <strong>NPI</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'taxId'}
                    direction={sortField === 'taxId' ? sortOrder : 'asc'}
                    onClick={() => handleSort('taxId')}
                  >
                    <strong>Tax ID</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell><strong>Contact</strong></TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'isActive'}
                    direction={sortField === 'isActive' ? sortOrder : 'asc'}
                    onClick={() => handleSort('isActive')}
                  >
                    <strong>Status</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'requiresSpecialHandling'}
                    direction={sortField === 'requiresSpecialHandling' ? sortOrder : 'asc'}
                    onClick={() => handleSort('requiresSpecialHandling')}
                  >
                    <strong>Special Handling</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedPayees.length > 0 ? (
                sortedPayees.map((payee) => (
                  <TableRow key={payee.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {payee.payeeId}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{payee.payeeName}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {payee.npi || 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {payee.taxId || 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {payee.contactEmail || payee.contactPhone ? (
                        <>
                          {payee.contactEmail && (
                            <Typography variant="body2">
                              {payee.contactEmail}
                            </Typography>
                          )}
                          {payee.contactPhone && (
                            <Typography variant="caption" color="textSecondary">
                              {payee.contactPhone}
                            </Typography>
                          )}
                        </>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          N/A
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={payee.isActive ? 'Active' : 'Inactive'}
                        color={payee.isActive ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={payee.requiresSpecialHandling ? 'Yes' : 'No'}
                        color={payee.requiresSpecialHandling ? 'warning' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => handleOpenDialog(payee)}
                          color="primary"
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteClick(payee)}
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
                      No payees configured. Click "Add Payee" to create one.
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
        <DialogTitle>{editingPayee ? 'Edit Payee' : 'Add New Payee'}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="Payee ID (Required)"
              value={formData.payeeId}
              onChange={(e) => setFormData({ ...formData, payeeId: e.target.value })}
              sx={{ mb: 2 }}
              required
              disabled={!!editingPayee}
            />
            <TextField
              fullWidth
              label="Payee Name (Required)"
              value={formData.payeeName}
              onChange={(e) => setFormData({ ...formData, payeeName: e.target.value })}
              sx={{ mb: 2 }}
              required
            />

            <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
              Identification (Optional)
            </Typography>
            <TextField
              fullWidth
              label="NPI (National Provider Identifier)"
              value={formData.npi}
              onChange={(e) => setFormData({ ...formData, npi: e.target.value })}
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              label="Tax ID"
              value={formData.taxId}
              onChange={(e) => setFormData({ ...formData, taxId: e.target.value })}
              sx={{ mb: 2 }}
            />

            <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
              Contact Information (Optional)
            </Typography>
            <TextField
              fullWidth
              label="Contact Email"
              type="email"
              value={formData.contactEmail}
              onChange={(e) => setFormData({ ...formData, contactEmail: e.target.value })}
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              label="Contact Phone"
              value={formData.contactPhone}
              onChange={(e) => setFormData({ ...formData, contactPhone: e.target.value })}
              sx={{ mb: 2 }}
            />

            <FormControlLabel
              control={
                <Switch
                  checked={formData.requiresSpecialHandling || false}
                  onChange={(e) => setFormData({ ...formData, requiresSpecialHandling: e.target.checked })}
                />
              }
              label="Requires Special Handling"
              sx={{ mb: 1 }}
            />
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
              editingPayee ? 'Update' : 'Create'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete payee "{editingPayee?.payeeName}"?
            This action cannot be undone.
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

export default PayeesConfig;
