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
  TableSortLabel,
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
  Visibility as VisibilityIcon,
  PlayArrow as PlayArrowIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { bucketService } from '../../services/bucketService';
import { Bucket, BucketStatus } from '../../types/models';
import { toast } from 'react-toastify';

const BucketList: React.FC = () => {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<BucketStatus | ''>('');
  const [payerFilter, setPayerFilter] = useState('');
  const [payeeFilter, setPayeeFilter] = useState('');
  const [orderBy, setOrderBy] = useState<keyof Bucket>('createdAt');
  const [order, setOrder] = useState<'asc' | 'desc'>('desc');

  // Fetch buckets
  const { data: buckets, isLoading, refetch } = useQuery<Bucket[]>({
    queryKey: ['buckets', statusFilter],
    queryFn: () => bucketService.getAllBuckets(statusFilter as BucketStatus),
  });

  // Sorting comparator
  const getComparator = (order: 'asc' | 'desc', orderBy: keyof Bucket) => {
    return (a: Bucket, b: Bucket) => {
      let aValue: any = a[orderBy];
      let bValue: any = b[orderBy];

      // Handle different data types
      if (orderBy === 'createdAt') {
        aValue = new Date(aValue as string).getTime();
        bValue = new Date(bValue as string).getTime();
      }

      if (typeof aValue === 'string' && typeof bValue === 'string') {
        return order === 'asc'
          ? aValue.localeCompare(bValue)
          : bValue.localeCompare(aValue);
      }

      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return order === 'asc' ? aValue - bValue : bValue - aValue;
      }

      return 0;
    };
  };

  // Handle sort request
  const handleSort = (property: keyof Bucket) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  // Get status color
  const getStatusColor = (status: BucketStatus): 'default' | 'primary' | 'warning' | 'info' | 'success' | 'error' => {
    switch (status) {
      case BucketStatus.ACCUMULATING:
        return 'primary';
      case BucketStatus.PENDING_APPROVAL:
        return 'warning';
      case BucketStatus.GENERATING:
        return 'info';
      case BucketStatus.COMPLETED:
        return 'success';
      case BucketStatus.FAILED:
        return 'error';
      default:
        return 'default';
    }
  };

  // Handle evaluate thresholds
  const handleEvaluate = async (bucketId: string) => {
    try {
      await bucketService.evaluateThresholds(bucketId);
      toast.success('Threshold evaluation triggered');
      refetch();
    } catch (error) {
      toast.error('Failed to evaluate thresholds');
    }
  };

  // Handle evaluate all
  const handleEvaluateAll = async () => {
    try {
      await bucketService.evaluateAllThresholds();
      toast.success('Evaluating thresholds for all buckets');
      refetch();
    } catch (error) {
      toast.error('Failed to evaluate all thresholds');
    }
  };

  // Filter buckets
  const filteredBuckets = buckets?.filter((bucket) => {
    const matchesPayer = !payerFilter || bucket.payerName.toLowerCase().includes(payerFilter.toLowerCase());
    const matchesPayee = !payeeFilter || bucket.payeeName.toLowerCase().includes(payeeFilter.toLowerCase());
    return matchesPayer && matchesPayee;
  });

  // Sort filtered buckets
  const sortedBuckets = filteredBuckets?.slice().sort(getComparator(order, orderBy));

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
            Buckets
          </Typography>
          <Typography variant="body1" color="textSecondary">
            View and manage EDI file buckets
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
            startIcon={<PlayArrowIcon />}
            onClick={handleEvaluateAll}
          >
            Evaluate All
          </Button>
        </Box>
      </Box>

      {/* Filters */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                select
                label="Status"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as BucketStatus | '')}
                size="small"
              >
                <MenuItem value="">All Statuses</MenuItem>
                <MenuItem value={BucketStatus.ACCUMULATING}>Accumulating</MenuItem>
                <MenuItem value={BucketStatus.PENDING_APPROVAL}>Pending Approval</MenuItem>
                <MenuItem value={BucketStatus.GENERATING}>Generating</MenuItem>
                <MenuItem value={BucketStatus.COMPLETED}>Completed</MenuItem>
                <MenuItem value={BucketStatus.FAILED}>Failed</MenuItem>
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

      {/* Buckets Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'bucketId'}
                    direction={orderBy === 'bucketId' ? order : 'asc'}
                    onClick={() => handleSort('bucketId')}
                  >
                    <strong>Bucket ID</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'payerName'}
                    direction={orderBy === 'payerName' ? order : 'asc'}
                    onClick={() => handleSort('payerName')}
                  >
                    <strong>Payer</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'payeeName'}
                    direction={orderBy === 'payeeName' ? order : 'asc'}
                    onClick={() => handleSort('payeeName')}
                  >
                    <strong>Payee</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'status'}
                    direction={orderBy === 'status' ? order : 'asc'}
                    onClick={() => handleSort('status')}
                  >
                    <strong>Status</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'claimCount'}
                    direction={orderBy === 'claimCount' ? order : 'asc'}
                    onClick={() => handleSort('claimCount')}
                  >
                    <strong>Claims</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={orderBy === 'totalAmount'}
                    direction={orderBy === 'totalAmount' ? order : 'asc'}
                    onClick={() => handleSort('totalAmount')}
                  >
                    <strong>Amount</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={orderBy === 'createdAt'}
                    direction={orderBy === 'createdAt' ? order : 'asc'}
                    onClick={() => handleSort('createdAt')}
                  >
                    <strong>Created</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedBuckets && sortedBuckets.length > 0 ? (
                sortedBuckets.map((bucket) => (
                  <TableRow key={bucket.bucketId} hover>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {bucket.bucketId.substring(0, 8)}...
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{bucket.payerName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {bucket.payerId}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{bucket.payeeName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {bucket.payeeId}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={bucket.status.replace('_', ' ')}
                        color={getStatusColor(bucket.status)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">{bucket.claimCount}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">
                        ${bucket.totalAmount.toLocaleString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(bucket.createdAt).toLocaleDateString()}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {new Date(bucket.createdAt).toLocaleTimeString()}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="View Details">
                        <IconButton
                          size="small"
                          onClick={() => navigate(`/buckets/${bucket.bucketId}`)}
                          color="primary"
                        >
                          <VisibilityIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {bucket.status === BucketStatus.ACCUMULATING && (
                        <Tooltip title="Evaluate Thresholds">
                          <IconButton
                            size="small"
                            onClick={() => handleEvaluate(bucket.bucketId)}
                            color="secondary"
                          >
                            <PlayArrowIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No buckets found
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Summary */}
      {filteredBuckets && filteredBuckets.length > 0 && (
        <Box mt={2}>
          <Typography variant="body2" color="textSecondary">
            Showing {filteredBuckets.length} bucket(s) •
            Total Claims: {filteredBuckets.reduce((sum, b) => sum + b.claimCount, 0)} •
            Total Amount: ${filteredBuckets.reduce((sum, b) => sum + b.totalAmount, 0).toLocaleString()}
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default BucketList;
