import React, { useEffect, useState, useMemo } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
  TableSortLabel,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { Payer } from '../../types/models';
import { configurationService } from '../../services/configurationService';
import { toast } from 'react-toastify';
import PayerForm from './PayerForm';

type SortField = 'payerId' | 'payerName' | 'isaSenderId' | 'gsApplicationSenderId' | 'sftpHost' | 'isActive';
type SortOrder = 'asc' | 'desc';

const PayerList: React.FC = () => {
  const [payers, setPayers] = useState<Payer[]>([]);
  const [filteredPayers, setFilteredPayers] = useState<Payer[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPayer, setSelectedPayer] = useState<Payer | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [payerToDelete, setPayerToDelete] = useState<Payer | null>(null);
  const [sortField, setSortField] = useState<SortField>('payerName');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

  useEffect(() => {
    loadPayers();
  }, []);

  useEffect(() => {
    if (searchQuery.trim() === '') {
      setFilteredPayers(payers);
    } else {
      const query = searchQuery.toLowerCase();
      const filtered = payers.filter(
        (payer) =>
          payer.payerId.toLowerCase().includes(query) ||
          payer.payerName.toLowerCase().includes(query) ||
          payer.isaSenderId?.toLowerCase().includes(query)
      );
      setFilteredPayers(filtered);
    }
  }, [searchQuery, payers]);

  // Sort payers based on current sort field and order
  const sortedPayers = useMemo(() => {
    return [...filteredPayers].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'payerId':
          aValue = a.payerId.toLowerCase();
          bValue = b.payerId.toLowerCase();
          break;
        case 'payerName':
          aValue = a.payerName.toLowerCase();
          bValue = b.payerName.toLowerCase();
          break;
        case 'isaSenderId':
          aValue = a.isaSenderId?.toLowerCase() || '';
          bValue = b.isaSenderId?.toLowerCase() || '';
          break;
        case 'gsApplicationSenderId':
          aValue = a.gsApplicationSenderId?.toLowerCase() || '';
          bValue = b.gsApplicationSenderId?.toLowerCase() || '';
          break;
        case 'sftpHost':
          aValue = a.sftpHost?.toLowerCase() || '';
          bValue = b.sftpHost?.toLowerCase() || '';
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
  }, [filteredPayers, sortField, sortOrder]);

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

  const loadPayers = async () => {
    setLoading(true);
    try {
      const data = await configurationService.getAllPayers();
      setPayers(data);
      setFilteredPayers(data);
    } catch (error: any) {
      console.error('Error loading payers:', error);
      toast.error('Failed to load payers');
    } finally {
      setLoading(false);
    }
  };

  const handleAddPayer = () => {
    setSelectedPayer(null);
    setShowForm(true);
  };

  const handleEditPayer = (payer: Payer) => {
    setSelectedPayer(payer);
    setShowForm(true);
  };

  const handleDeleteClick = (payer: Payer) => {
    setPayerToDelete(payer);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!payerToDelete) return;

    try {
      await configurationService.deletePayer(payerToDelete.id);
      toast.success(`Payer ${payerToDelete.payerName} deleted successfully`);
      loadPayers();
    } catch (error: any) {
      console.error('Error deleting payer:', error);
      toast.error('Failed to delete payer');
    } finally {
      setDeleteDialogOpen(false);
      setPayerToDelete(null);
    }
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setSelectedPayer(null);
    loadPayers();
  };

  const handleFormCancel = () => {
    setShowForm(false);
    setSelectedPayer(null);
  };

  if (showForm) {
    return (
      <Box>
        <PayerForm payer={selectedPayer || undefined} onSuccess={handleFormSuccess} onCancel={handleFormCancel} />
      </Box>
    );
  }

  return (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h5">Payer Management</Typography>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Button variant="contained" color="primary" startIcon={<AddIcon />} onClick={handleAddPayer}>
                Add Payer
              </Button>
              <Tooltip title="Refresh">
                <IconButton onClick={loadPayers} disabled={loading}>
                  <RefreshIcon />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>

          <TextField
            fullWidth
            variant="outlined"
            placeholder="Search by Payer ID, Name, or ISA Sender ID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            InputProps={{
              startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
            }}
          />
        </CardContent>
      </Card>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'payerId'}
                  direction={sortField === 'payerId' ? sortOrder : 'asc'}
                  onClick={() => handleSort('payerId')}
                >
                  Payer ID
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'payerName'}
                  direction={sortField === 'payerName' ? sortOrder : 'asc'}
                  onClick={() => handleSort('payerName')}
                >
                  Payer Name
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'isaSenderId'}
                  direction={sortField === 'isaSenderId' ? sortOrder : 'asc'}
                  onClick={() => handleSort('isaSenderId')}
                >
                  ISA Sender ID
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'gsApplicationSenderId'}
                  direction={sortField === 'gsApplicationSenderId' ? sortOrder : 'asc'}
                  onClick={() => handleSort('gsApplicationSenderId')}
                >
                  GS App Sender ID
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'sftpHost'}
                  direction={sortField === 'sftpHost' ? sortOrder : 'asc'}
                  onClick={() => handleSort('sftpHost')}
                >
                  SFTP Host
                </TableSortLabel>
              </TableCell>
              <TableCell align="center">
                <TableSortLabel
                  active={sortField === 'isActive'}
                  direction={sortField === 'isActive' ? sortOrder : 'asc'}
                  onClick={() => handleSort('isActive')}
                >
                  Status
                </TableSortLabel>
              </TableCell>
              <TableCell align="center">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  Loading payers...
                </TableCell>
              </TableRow>
            ) : sortedPayers.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  {searchQuery ? 'No payers found matching your search' : 'No payers configured yet'}
                </TableCell>
              </TableRow>
            ) : (
              sortedPayers.map((payer) => (
                <TableRow key={payer.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight="bold">
                      {payer.payerId}
                    </Typography>
                  </TableCell>
                  <TableCell>{payer.payerName}</TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {payer.isaSenderId}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {payer.gsApplicationSenderId || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontSize="0.85rem">
                      {payer.sftpHost || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={payer.isActive ? 'Active' : 'Inactive'}
                      color={payer.isActive ? 'success' : 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title="Edit">
                      <IconButton size="small" color="primary" onClick={() => handleEditPayer(payer)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => handleDeleteClick(payer)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete payer <strong>{payerToDelete?.payerName}</strong> ({payerToDelete?.payerId})?
            <br />
            <br />
            This action cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained">
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PayerList;
