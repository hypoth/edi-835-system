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
  Chip,
  IconButton,
  TextField,
  Grid,
  MenuItem,
  Button,
  CircularProgress,
  Tooltip,
  TableSortLabel,
} from '@mui/material';
import {
  Download as DownloadIcon,
  Refresh as RefreshIcon,
  Replay as ReplayIcon,
  Visibility as VisibilityIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fileService } from '../../services/fileService';
import { FileHistory, DeliveryStatus } from '../../types/models';
import { toast } from 'react-toastify';

type SortField = 'fileName' | 'payerName' | 'deliveryStatus' | 'fileSizeBytes' | 'claimCount' | 'totalAmount' | 'generatedAt' | 'deliveredAt';
type SortOrder = 'asc' | 'desc';

const FileList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<DeliveryStatus | ''>('');
  const [payerFilter, setPayerFilter] = useState('');
  const [payeeFilter, setPayeeFilter] = useState('');
  const [sortField, setSortField] = useState<SortField>('generatedAt');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

  // Fetch files
  const { data: files, isLoading, refetch } = useQuery<FileHistory[]>({
    queryKey: ['files', statusFilter],
    queryFn: () => fileService.getAllFiles(statusFilter as DeliveryStatus),
  });

  // Get status color
  const getStatusColor = (status: DeliveryStatus): 'default' | 'primary' | 'success' | 'error' | 'warning' => {
    switch (status) {
      case DeliveryStatus.PENDING:
        return 'primary';
      case DeliveryStatus.DELIVERED:
        return 'success';
      case DeliveryStatus.FAILED:
        return 'error';
      case DeliveryStatus.RETRY:
        return 'warning';
      default:
        return 'default';
    }
  };

  // Retry delivery mutation
  const retryMutation = useMutation({
    mutationFn: (fileId: string) => fileService.retryDelivery(fileId),
    onSuccess: () => {
      toast.success('Delivery retry initiated');
      queryClient.invalidateQueries({ queryKey: ['files'] });
    },
    onError: () => {
      toast.error('Failed to retry delivery');
    },
  });

  // Download file mutation
  const downloadMutation = useMutation({
    mutationFn: async (file: FileHistory) => {
      const blob = await fileService.downloadFile(file.fileId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = file.fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    },
    onSuccess: () => {
      toast.success('File downloaded successfully');
    },
    onError: () => {
      toast.error('Failed to download file');
    },
  });

  const handleRetryDelivery = (fileId: string) => {
    retryMutation.mutate(fileId);
  };

  const handleDownload = (file: FileHistory) => {
    downloadMutation.mutate(file);
  };

  // Filter files
  const filteredFiles = files?.filter((file) => {
    const matchesPayer = !payerFilter || file.bucket.payerName.toLowerCase().includes(payerFilter.toLowerCase());
    const matchesPayee = !payeeFilter || file.bucket.payeeName.toLowerCase().includes(payeeFilter.toLowerCase());
    return matchesPayer && matchesPayee;
  });

  // Sort files based on current sort field and order
  const sortedFiles = useMemo(() => {
    if (!filteredFiles) return [];

    return [...filteredFiles].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'fileName':
          aValue = a.fileName.toLowerCase();
          bValue = b.fileName.toLowerCase();
          break;
        case 'payerName':
          aValue = a.bucket.payerName.toLowerCase();
          bValue = b.bucket.payerName.toLowerCase();
          break;
        case 'deliveryStatus':
          aValue = a.deliveryStatus;
          bValue = b.deliveryStatus;
          break;
        case 'fileSizeBytes':
          aValue = a.fileSizeBytes;
          bValue = b.fileSizeBytes;
          break;
        case 'claimCount':
          aValue = a.claimCount;
          bValue = b.claimCount;
          break;
        case 'totalAmount':
          aValue = a.totalAmount;
          bValue = b.totalAmount;
          break;
        case 'generatedAt':
          aValue = new Date(a.generatedAt).getTime();
          bValue = new Date(b.generatedAt).getTime();
          break;
        case 'deliveredAt':
          aValue = a.deliveredAt ? new Date(a.deliveredAt).getTime() : 0;
          bValue = b.deliveredAt ? new Date(b.deliveredAt).getTime() : 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }, [filteredFiles, sortField, sortOrder]);

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

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
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
            File Management
          </Typography>
          <Typography variant="body1" color="textSecondary">
            View and manage generated EDI files
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

      {/* Filters */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                select
                label="Delivery Status"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as DeliveryStatus | '')}
                size="small"
              >
                <MenuItem value="">All Statuses</MenuItem>
                <MenuItem value={DeliveryStatus.PENDING}>Pending</MenuItem>
                <MenuItem value={DeliveryStatus.DELIVERED}>Delivered</MenuItem>
                <MenuItem value={DeliveryStatus.FAILED}>Failed</MenuItem>
                <MenuItem value={DeliveryStatus.RETRY}>Retry</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                label="Payer"
                value={payerFilter}
                onChange={(e) => setPayerFilter(e.target.value)}
                placeholder="Filter by payer name"
                size="small"
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                label="Payee"
                value={payeeFilter}
                onChange={(e) => setPayeeFilter(e.target.value)}
                placeholder="Filter by payee name"
                size="small"
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Files Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'fileName'}
                    direction={sortField === 'fileName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('fileName')}
                  >
                    <strong>File Name</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'payerName'}
                    direction={sortField === 'payerName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('payerName')}
                  >
                    <strong>Payer / Payee</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'deliveryStatus'}
                    direction={sortField === 'deliveryStatus' ? sortOrder : 'asc'}
                    onClick={() => handleSort('deliveryStatus')}
                  >
                    <strong>Status</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'fileSizeBytes'}
                    direction={sortField === 'fileSizeBytes' ? sortOrder : 'asc'}
                    onClick={() => handleSort('fileSizeBytes')}
                  >
                    <strong>Size</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'claimCount'}
                    direction={sortField === 'claimCount' ? sortOrder : 'asc'}
                    onClick={() => handleSort('claimCount')}
                  >
                    <strong>Claims</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'totalAmount'}
                    direction={sortField === 'totalAmount' ? sortOrder : 'asc'}
                    onClick={() => handleSort('totalAmount')}
                  >
                    <strong>Amount</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'generatedAt'}
                    direction={sortField === 'generatedAt' ? sortOrder : 'asc'}
                    onClick={() => handleSort('generatedAt')}
                  >
                    <strong>Generated</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'deliveredAt'}
                    direction={sortField === 'deliveredAt' ? sortOrder : 'asc'}
                    onClick={() => handleSort('deliveredAt')}
                  >
                    <strong>Delivered</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedFiles.length > 0 ? (
                sortedFiles.map((file) => (
                  <TableRow key={file.fileId} hover>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {file.fileName}
                      </Typography>
                      {file.retryCount > 0 && (
                        <Typography variant="caption" color="warning.main">
                          Retry attempts: {file.retryCount}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{file.bucket.payerName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        → {file.bucket.payeeName}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={file.deliveryStatus}
                        color={getStatusColor(file.deliveryStatus)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">
                        {formatFileSize(file.fileSizeBytes)}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">{file.claimCount}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">
                        ${file.totalAmount.toLocaleString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(file.generatedAt).toLocaleDateString()}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {new Date(file.generatedAt).toLocaleTimeString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {file.deliveredAt ? (
                        <>
                          <Typography variant="body2">
                            {new Date(file.deliveredAt).toLocaleDateString()}
                          </Typography>
                          <Typography variant="caption" color="textSecondary">
                            {new Date(file.deliveredAt).toLocaleTimeString()}
                          </Typography>
                        </>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          Not delivered
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Download File">
                        <IconButton
                          size="small"
                          onClick={() => handleDownload(file)}
                          color="primary"
                          disabled={downloadMutation.isPending}
                        >
                          <DownloadIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="View Details">
                        <IconButton
                          size="small"
                          onClick={() => navigate(`/buckets/${file.bucket.bucketId}`)}
                          color="info"
                        >
                          <VisibilityIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {(file.deliveryStatus === DeliveryStatus.FAILED ||
                        file.deliveryStatus === DeliveryStatus.RETRY) && (
                        <Tooltip title="Retry Delivery">
                          <IconButton
                            size="small"
                            onClick={() => handleRetryDelivery(file.fileId)}
                            color="warning"
                            disabled={retryMutation.isPending}
                          >
                            <ReplayIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={9} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No files found
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Summary */}
      {sortedFiles.length > 0 && (
        <Box mt={2}>
          <Typography variant="body2" color="textSecondary">
            Showing {sortedFiles.length} file(s) •
            Total Size: {formatFileSize(sortedFiles.reduce((sum, f) => sum + f.fileSizeBytes, 0))} •
            Total Claims: {sortedFiles.reduce((sum, f) => sum + f.claimCount, 0)} •
            Total Amount: ${sortedFiles.reduce((sum, f) => sum + f.totalAmount, 0).toLocaleString()}
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default FileList;
