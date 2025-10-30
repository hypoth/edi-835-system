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
  Chip,
  IconButton,
  TextField,
  Grid,
  MenuItem,
  Button,
  CircularProgress,
  Tooltip,
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

const FileList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<DeliveryStatus | ''>('');
  const [payerFilter, setPayerFilter] = useState('');
  const [payeeFilter, setPayeeFilter] = useState('');

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
                <TableCell><strong>File Name</strong></TableCell>
                <TableCell><strong>Payer / Payee</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell align="right"><strong>Size</strong></TableCell>
                <TableCell align="right"><strong>Claims</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Generated</strong></TableCell>
                <TableCell><strong>Delivered</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredFiles && filteredFiles.length > 0 ? (
                filteredFiles.map((file) => (
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
      {filteredFiles && filteredFiles.length > 0 && (
        <Box mt={2}>
          <Typography variant="body2" color="textSecondary">
            Showing {filteredFiles.length} file(s) •
            Total Size: {formatFileSize(filteredFiles.reduce((sum, f) => sum + f.fileSizeBytes, 0))} •
            Total Claims: {filteredFiles.reduce((sum, f) => sum + f.claimCount, 0)} •
            Total Amount: ${filteredFiles.reduce((sum, f) => sum + f.totalAmount, 0).toLocaleString()}
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default FileList;
