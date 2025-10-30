import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  CircularProgress,
  Alert,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Breadcrumbs,
  Link,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  PlayArrow as PlayArrowIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Timeline as TimelineIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { bucketService } from '../../services/bucketService';
import { Bucket, BucketStatus } from '../../types/models';
import { toast } from 'react-toastify';

const BucketDetails: React.FC = () => {
  const { bucketId } = useParams<{ bucketId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [transitionDialogOpen, setTransitionDialogOpen] = React.useState(false);
  const [transitionType, setTransitionType] = React.useState<'approval' | 'generation' | 'complete' | 'fail' | null>(null);
  const [comments, setComments] = React.useState('');

  // Fetch bucket details
  const { data: bucket, isLoading, error } = useQuery<Bucket>({
    queryKey: ['bucket', bucketId],
    queryFn: () => bucketService.getBucketById(bucketId!),
    enabled: !!bucketId,
    refetchInterval: 10000, // Refresh every 10 seconds
  });

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

  // Mutation for evaluating thresholds
  const evaluateMutation = useMutation({
    mutationFn: () => bucketService.evaluateThresholds(bucketId!),
    onSuccess: () => {
      toast.success('Threshold evaluation triggered');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
    },
    onError: () => {
      toast.error('Failed to evaluate thresholds');
    },
  });

  // Mutation for transitioning to approval
  const transitionToApprovalMutation = useMutation({
    mutationFn: () => bucketService.transitionToPendingApproval(bucketId!),
    onSuccess: () => {
      toast.success('Bucket transitioned to pending approval');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      setTransitionDialogOpen(false);
    },
    onError: () => {
      toast.error('Failed to transition bucket');
    },
  });

  // Mutation for transitioning to generation
  const transitionToGenerationMutation = useMutation({
    mutationFn: () => bucketService.transitionToGeneration(bucketId!),
    onSuccess: () => {
      toast.success('Bucket transitioned to generation');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      setTransitionDialogOpen(false);
    },
    onError: () => {
      toast.error('Failed to transition bucket');
    },
  });

  // Mutation for marking completed
  const markCompletedMutation = useMutation({
    mutationFn: () => bucketService.markCompleted(bucketId!),
    onSuccess: () => {
      toast.success('Bucket marked as completed');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      setTransitionDialogOpen(false);
    },
    onError: () => {
      toast.error('Failed to mark bucket as completed');
    },
  });

  // Mutation for marking failed
  const markFailedMutation = useMutation({
    mutationFn: () => bucketService.markFailed(bucketId!),
    onSuccess: () => {
      toast.success('Bucket marked as failed');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      setTransitionDialogOpen(false);
    },
    onError: () => {
      toast.error('Failed to mark bucket as failed');
    },
  });

  const handleTransitionClick = (type: 'approval' | 'generation' | 'complete' | 'fail') => {
    setTransitionType(type);
    setTransitionDialogOpen(true);
  };

  const handleTransitionConfirm = () => {
    switch (transitionType) {
      case 'approval':
        transitionToApprovalMutation.mutate();
        break;
      case 'generation':
        transitionToGenerationMutation.mutate();
        break;
      case 'complete':
        markCompletedMutation.mutate();
        break;
      case 'fail':
        markFailedMutation.mutate();
        break;
    }
    setComments('');
  };

  const handleTransitionCancel = () => {
    setTransitionDialogOpen(false);
    setTransitionType(null);
    setComments('');
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (error || !bucket) {
    return (
      <Box>
        <Alert severity="error">
          Failed to load bucket details. The bucket may not exist or there was an error loading the data.
        </Alert>
        <Button
          variant="outlined"
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/buckets')}
          sx={{ mt: 2 }}
        >
          Back to Buckets
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link
          color="inherit"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate('/buckets');
          }}
          sx={{ cursor: 'pointer' }}
        >
          Buckets
        </Link>
        <Typography color="text.primary">Bucket Details</Typography>
      </Breadcrumbs>

      {/* Header */}
      <Box mb={3} display="flex" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={600} gutterBottom>
            Bucket Details
          </Typography>
          <Typography variant="body2" fontFamily="monospace" color="textSecondary">
            ID: {bucket.bucketId}
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/buckets')}
        >
          Back to List
        </Button>
      </Box>

      {/* Bucket Overview */}
      <Grid container spacing={3} mb={3}>
        {/* Basic Information */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight={600} gutterBottom>
                Basic Information
              </Typography>
              <Divider sx={{ mb: 2 }} />

              <Grid container spacing={2}>
                <Grid item xs={4}>
                  <Typography variant="body2" color="textSecondary">
                    Status
                  </Typography>
                </Grid>
                <Grid item xs={8}>
                  <Chip
                    label={bucket.status.replace('_', ' ')}
                    color={getStatusColor(bucket.status)}
                    size="small"
                  />
                </Grid>

                <Grid item xs={4}>
                  <Typography variant="body2" color="textSecondary">
                    Payer
                  </Typography>
                </Grid>
                <Grid item xs={8}>
                  <Typography variant="body2" fontWeight={600}>
                    {bucket.payerName}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    ID: {bucket.payerId}
                  </Typography>
                </Grid>

                <Grid item xs={4}>
                  <Typography variant="body2" color="textSecondary">
                    Payee
                  </Typography>
                </Grid>
                <Grid item xs={8}>
                  <Typography variant="body2" fontWeight={600}>
                    {bucket.payeeName}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    ID: {bucket.payeeId}
                  </Typography>
                </Grid>

                {bucket.binNumber && (
                  <>
                    <Grid item xs={4}>
                      <Typography variant="body2" color="textSecondary">
                        BIN
                      </Typography>
                    </Grid>
                    <Grid item xs={8}>
                      <Typography variant="body2">{bucket.binNumber}</Typography>
                    </Grid>
                  </>
                )}

                {bucket.pcnNumber && (
                  <>
                    <Grid item xs={4}>
                      <Typography variant="body2" color="textSecondary">
                        PCN
                      </Typography>
                    </Grid>
                    <Grid item xs={8}>
                      <Typography variant="body2">{bucket.pcnNumber}</Typography>
                    </Grid>
                  </>
                )}

                <Grid item xs={4}>
                  <Typography variant="body2" color="textSecondary">
                    Bucketing Rule
                  </Typography>
                </Grid>
                <Grid item xs={8}>
                  <Typography variant="body2">{bucket.bucketingRuleName}</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Statistics */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight={600} gutterBottom>
                Statistics
              </Typography>
              <Divider sx={{ mb: 2 }} />

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Box
                    sx={{
                      p: 2,
                      backgroundColor: 'primary.light',
                      borderRadius: 2,
                      textAlign: 'center',
                    }}
                  >
                    <Typography variant="h4" fontWeight={600} color="primary.main">
                      {bucket.claimCount}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Claims
                    </Typography>
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Box
                    sx={{
                      p: 2,
                      backgroundColor: 'success.light',
                      borderRadius: 2,
                      textAlign: 'center',
                    }}
                  >
                    <Typography variant="h4" fontWeight={600} color="success.main">
                      ${bucket.totalAmount.toLocaleString()}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Total Amount
                    </Typography>
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Timeline */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            <TimelineIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
            Bucket Timeline
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>Event</strong></TableCell>
                  <TableCell><strong>Timestamp</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow>
                  <TableCell>Created</TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {new Date(bucket.createdAt).toLocaleString()}
                    </Typography>
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Last Updated</TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {new Date(bucket.updatedAt).toLocaleString()}
                    </Typography>
                  </TableCell>
                </TableRow>
                {bucket.awaitingApprovalSince && (
                  <TableRow>
                    <TableCell>Awaiting Approval Since</TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(bucket.awaitingApprovalSince).toLocaleString()}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
                {bucket.generationStartedAt && (
                  <TableRow>
                    <TableCell>Generation Started</TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(bucket.generationStartedAt).toLocaleString()}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
                {bucket.generationCompletedAt && (
                  <TableRow>
                    <TableCell>Generation Completed</TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(bucket.generationCompletedAt).toLocaleString()}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Actions */}
      <Card>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            Available Actions
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Box display="flex" gap={2} flexWrap="wrap">
            {bucket.status === BucketStatus.ACCUMULATING && (
              <>
                <Button
                  variant="contained"
                  startIcon={<PlayArrowIcon />}
                  onClick={() => evaluateMutation.mutate()}
                  disabled={evaluateMutation.isPending}
                >
                  Evaluate Thresholds
                </Button>
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<CheckCircleIcon />}
                  onClick={() => handleTransitionClick('approval')}
                >
                  Transition to Approval
                </Button>
                <Button
                  variant="outlined"
                  color="info"
                  startIcon={<PlayArrowIcon />}
                  onClick={() => handleTransitionClick('generation')}
                >
                  Transition to Generation
                </Button>
              </>
            )}

            {bucket.status === BucketStatus.PENDING_APPROVAL && (
              <Button
                variant="contained"
                color="warning"
                onClick={() => navigate('/approvals')}
              >
                Go to Approval Queue
              </Button>
            )}

            {bucket.status === BucketStatus.GENERATING && (
              <Alert severity="info" sx={{ width: '100%' }}>
                This bucket is currently generating an EDI file. Please wait for the process to complete.
              </Alert>
            )}

            {bucket.status === BucketStatus.COMPLETED && (
              <Alert severity="success" sx={{ width: '100%' }}>
                This bucket has been completed. View the generated file in the File Management section.
              </Alert>
            )}

            {bucket.status === BucketStatus.FAILED && (
              <>
                <Alert severity="error" sx={{ width: '100%', mb: 2 }}>
                  This bucket has failed during processing. You can retry or investigate the error.
                </Alert>
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<CancelIcon />}
                  onClick={() => handleTransitionClick('fail')}
                >
                  Retry Processing
                </Button>
              </>
            )}
          </Box>
        </CardContent>
      </Card>

      {/* Transition Confirmation Dialog */}
      <Dialog open={transitionDialogOpen} onClose={handleTransitionCancel} maxWidth="sm" fullWidth>
        <DialogTitle>
          Confirm State Transition
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="textSecondary" gutterBottom>
            {transitionType === 'approval' && 'This will move the bucket to pending approval status.'}
            {transitionType === 'generation' && 'This will start the EDI file generation process immediately.'}
            {transitionType === 'complete' && 'This will mark the bucket as completed.'}
            {transitionType === 'fail' && 'This will mark the bucket as failed.'}
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={3}
            label="Comments (Optional)"
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleTransitionCancel}>Cancel</Button>
          <Button
            onClick={handleTransitionConfirm}
            variant="contained"
            color="primary"
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default BucketDetails;
