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
  Warning as WarningIcon,
  Add as AddIcon,
  Payment as PaymentIcon,
  Settings as SettingsIcon,
  AccountBalance as AccountBalanceIcon,
  Error as ErrorIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  SwapHoriz as SwapHorizIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { bucketService, BucketConfiguration } from '../../services/bucketService';
import { checkPaymentService } from '../../services/checkPaymentService';
import { Bucket, BucketStatus, BucketConfigurationCheck, CreatePayerFromBucketRequest, CreatePayeeFromBucketRequest, CheckPayment, ManualCheckAssignmentRequest } from '../../types/models';
import { toast } from 'react-toastify';
import CreatePayerForm from '../../components/buckets/CreatePayerForm';
import CreatePayeeForm from '../../components/buckets/CreatePayeeForm';
import ReplaceCheckDialog from '../../components/checkpayments/ReplaceCheckDialog';

const BucketDetails: React.FC = () => {
  const { bucketId } = useParams<{ bucketId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [transitionDialogOpen, setTransitionDialogOpen] = React.useState(false);
  const [transitionType, setTransitionType] = React.useState<'approval' | 'generation' | 'complete' | 'fail' | null>(null);
  const [comments, setComments] = React.useState('');
  const [createPayerOpen, setCreatePayerOpen] = React.useState(false);
  const [createPayeeOpen, setCreatePayeeOpen] = React.useState(false);
  const [errorExpanded, setErrorExpanded] = React.useState(false);
  const [replaceCheckOpen, setReplaceCheckOpen] = React.useState(false);

  // Fetch bucket details
  const { data: bucket, isLoading, error } = useQuery<Bucket>({
    queryKey: ['bucket', bucketId],
    queryFn: () => bucketService.getBucketById(bucketId!),
    enabled: !!bucketId,
    refetchInterval: 10000, // Refresh every 10 seconds
  });

  // Fetch configuration check (only for FAILED or MISSING_CONFIGURATION status)
  const { data: configCheck } = useQuery<BucketConfigurationCheck>({
    queryKey: ['bucket-config-check', bucketId],
    queryFn: () => bucketService.checkConfiguration(bucketId!),
    enabled: !!bucketId && !!bucket && (bucket.status === BucketStatus.FAILED || bucket.status === BucketStatus.MISSING_CONFIGURATION),
    refetchInterval: 10000,
  });

  // Fetch check payment details (for COMPLETED, GENERATING, or PENDING_APPROVAL buckets with payment)
  const { data: checkPayment, isLoading: checkPaymentLoading } = useQuery<CheckPayment | null>({
    queryKey: ['bucket-check-payment', bucketId],
    queryFn: () => checkPaymentService.getCheckPaymentForBucket(bucketId!),
    enabled: !!bucketId && !!bucket && (
      bucket.status === BucketStatus.COMPLETED ||
      bucket.status === BucketStatus.GENERATING ||
      bucket.status === BucketStatus.PENDING_APPROVAL
    ),
  });

  // Fetch bucket configuration (for ACCUMULATING, PENDING_APPROVAL, or COMPLETED buckets)
  const { data: bucketConfig, isLoading: configLoading } = useQuery<BucketConfiguration>({
    queryKey: ['bucket-configuration', bucketId],
    queryFn: () => bucketService.getBucketConfiguration(bucketId!),
    enabled: !!bucketId && !!bucket && (
      bucket.status === BucketStatus.ACCUMULATING ||
      bucket.status === BucketStatus.PENDING_APPROVAL ||
      bucket.status === BucketStatus.COMPLETED ||
      bucket.status === BucketStatus.GENERATING
    ),
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
      case BucketStatus.MISSING_CONFIGURATION:
        return 'warning';
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

  // Mutation for creating payer
  const createPayerMutation = useMutation({
    mutationFn: (data: CreatePayerFromBucketRequest) => bucketService.createPayerFromBucket(data),
    onSuccess: () => {
      toast.success('Payer created successfully');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      queryClient.invalidateQueries({ queryKey: ['bucket-config-check', bucketId] });
      setCreatePayerOpen(false);
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to create payer');
    },
  });

  // Mutation for creating payee
  const createPayeeMutation = useMutation({
    mutationFn: (data: CreatePayeeFromBucketRequest) => bucketService.createPayeeFromBucket(data),
    onSuccess: () => {
      toast.success('Payee created successfully');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      queryClient.invalidateQueries({ queryKey: ['bucket-config-check', bucketId] });
      setCreatePayeeOpen(false);
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to create payee');
    },
  });

  // Mutation for replacing check
  const replaceCheckMutation = useMutation({
    mutationFn: (request: ManualCheckAssignmentRequest) =>
      checkPaymentService.replaceCheck(bucketId!, request),
    onSuccess: (result) => {
      toast.success(result.message || 'Check replaced successfully');
      queryClient.invalidateQueries({ queryKey: ['bucket', bucketId] });
      queryClient.invalidateQueries({ queryKey: ['bucket-check-payment', bucketId] });
      setReplaceCheckOpen(false);
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to replace check');
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

      {/* Payment Details - for COMPLETED or buckets with payment assigned */}
      {(bucket.status === BucketStatus.COMPLETED || bucket.status === BucketStatus.GENERATING || checkPayment) && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" fontWeight={600} gutterBottom>
              <PaymentIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
              Payment Details
            </Typography>
            <Divider sx={{ mb: 2 }} />

            {checkPaymentLoading ? (
              <Box display="flex" justifyContent="center" py={2}>
                <CircularProgress size={24} />
              </Box>
            ) : checkPayment ? (
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    <Typography variant="body2">
                      <strong>Payment Method:</strong> Check Payment
                    </Typography>
                  </Alert>
                </Grid>

                <Grid item xs={12} md={6}>
                  <Card variant="outlined" sx={{ p: 2, backgroundColor: 'grey.50' }}>
                    <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                      <AccountBalanceIcon sx={{ fontSize: 16, mr: 0.5, verticalAlign: 'middle' }} />
                      Check Information
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    <Grid container spacing={1}>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Check Number:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2" fontWeight={600}>{checkPayment.checkNumber}</Typography>
                      </Grid>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Check Amount:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2" fontWeight={600} color="success.main">
                          ${checkPayment.checkAmount?.toLocaleString()}
                        </Typography>
                      </Grid>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Check Date:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2">{checkPayment.checkDate}</Typography>
                      </Grid>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Status:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Chip
                          label={checkPayment.status}
                          size="small"
                          color={
                            checkPayment.status === 'ISSUED' ? 'success' :
                            checkPayment.status === 'ACKNOWLEDGED' ? 'info' :
                            checkPayment.status === 'ASSIGNED' ? 'warning' :
                            checkPayment.status === 'VOID' ? 'error' : 'default'
                          }
                        />
                      </Grid>
                    </Grid>
                  </Card>
                </Grid>

                <Grid item xs={12} md={6}>
                  <Card variant="outlined" sx={{ p: 2, backgroundColor: 'grey.50' }}>
                    <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                      Bank Details
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    <Grid container spacing={1}>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Bank Name:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2">{checkPayment.bankName || 'N/A'}</Typography>
                      </Grid>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Routing Number:</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2">{checkPayment.routingNumber || 'N/A'}</Typography>
                      </Grid>
                      <Grid item xs={5}>
                        <Typography variant="body2" color="textSecondary">Account (Last 4):</Typography>
                      </Grid>
                      <Grid item xs={7}>
                        <Typography variant="body2">
                          {checkPayment.accountNumberLast4 ? `****${checkPayment.accountNumberLast4}` : 'N/A'}
                        </Typography>
                      </Grid>
                    </Grid>
                  </Card>
                </Grid>

                <Grid item xs={12}>
                  <Card variant="outlined" sx={{ p: 2, backgroundColor: 'grey.50' }}>
                    <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                      Assignment Details
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    <Grid container spacing={1}>
                      <Grid item xs={3}>
                        <Typography variant="body2" color="textSecondary">Assigned By:</Typography>
                      </Grid>
                      <Grid item xs={3}>
                        <Typography variant="body2">
                          {checkPayment.assignedBy === 'SYSTEM' ? (
                            <Chip label="Auto-Assigned" size="small" color="primary" variant="outlined" />
                          ) : checkPayment.assignedBy || 'N/A'}
                        </Typography>
                      </Grid>
                      <Grid item xs={3}>
                        <Typography variant="body2" color="textSecondary">Assigned At:</Typography>
                      </Grid>
                      <Grid item xs={3}>
                        <Typography variant="body2">
                          {checkPayment.assignedAt ? new Date(checkPayment.assignedAt).toLocaleString() : 'N/A'}
                        </Typography>
                      </Grid>
                      {checkPayment.acknowledgedBy && (
                        <>
                          <Grid item xs={3}>
                            <Typography variant="body2" color="textSecondary">Acknowledged By:</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2">{checkPayment.acknowledgedBy}</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2" color="textSecondary">Acknowledged At:</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2">
                              {checkPayment.acknowledgedAt ? new Date(checkPayment.acknowledgedAt).toLocaleString() : 'N/A'}
                            </Typography>
                          </Grid>
                        </>
                      )}
                      {checkPayment.issuedBy && (
                        <>
                          <Grid item xs={3}>
                            <Typography variant="body2" color="textSecondary">Issued By:</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2">{checkPayment.issuedBy}</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2" color="textSecondary">Issued At:</Typography>
                          </Grid>
                          <Grid item xs={3}>
                            <Typography variant="body2">
                              {checkPayment.issuedAt ? new Date(checkPayment.issuedAt).toLocaleString() : 'N/A'}
                            </Typography>
                          </Grid>
                        </>
                      )}
                    </Grid>
                  </Card>
                </Grid>

                {/* Replace Check Button - Only for PENDING_APPROVAL buckets with ASSIGNED check */}
                {bucket.status === BucketStatus.PENDING_APPROVAL && checkPayment.status === 'ASSIGNED' && (
                  <Grid item xs={12}>
                    <Box display="flex" justifyContent="flex-end">
                      <Button
                        variant="outlined"
                        color="warning"
                        startIcon={<SwapHorizIcon />}
                        onClick={() => setReplaceCheckOpen(true)}
                      >
                        Replace Check
                      </Button>
                    </Box>
                  </Grid>
                )}
              </Grid>
            ) : (
              <Alert severity="info">
                <Typography variant="body2">
                  <strong>Payment Method:</strong> EFT (Electronic Funds Transfer) or No Check Assigned
                </Typography>
              </Alert>
            )}
          </CardContent>
        </Card>
      )}

      {/* Configuration Overview/Used - for ACCUMULATING, PENDING_APPROVAL, COMPLETED, or GENERATING buckets */}
      {(bucket.status === BucketStatus.ACCUMULATING ||
        bucket.status === BucketStatus.PENDING_APPROVAL ||
        bucket.status === BucketStatus.COMPLETED ||
        bucket.status === BucketStatus.GENERATING) && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" fontWeight={600} gutterBottom>
              <SettingsIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
              {bucket.status === BucketStatus.COMPLETED || bucket.status === BucketStatus.GENERATING
                ? 'Configuration Used'
                : 'Configuration Overview'}
            </Typography>
            <Divider sx={{ mb: 2 }} />

            {configLoading ? (
              <Box display="flex" justifyContent="center" py={2}>
                <CircularProgress size={24} />
              </Box>
            ) : bucketConfig ? (
              <Grid container spacing={2}>
                {/* Threshold Configuration */}
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, height: '100%' }}>
                    <Typography variant="subtitle2" color="primary" fontWeight={600} gutterBottom>
                      Generation Threshold
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    {bucketConfig.threshold ? (
                      <Box>
                        <Typography variant="body2" fontWeight={600} gutterBottom>
                          {bucketConfig.threshold.thresholdName}
                        </Typography>
                        <Chip
                          label={bucketConfig.threshold.thresholdType}
                          size="small"
                          color="primary"
                          variant="outlined"
                          sx={{ mb: 1 }}
                        />
                        <Box sx={{ mt: 1 }}>
                          {bucketConfig.threshold.maxClaims && (
                            <Typography variant="body2" color="textSecondary">
                              Max Claims: <strong>{bucketConfig.threshold.maxClaims}</strong>
                            </Typography>
                          )}
                          {bucketConfig.threshold.maxAmount && (
                            <Typography variant="body2" color="textSecondary">
                              Max Amount: <strong>${bucketConfig.threshold.maxAmount.toLocaleString()}</strong>
                            </Typography>
                          )}
                          {bucketConfig.threshold.timeDuration && (
                            <Typography variant="body2" color="textSecondary">
                              Time Duration: <strong>{bucketConfig.threshold.timeDuration}</strong>
                            </Typography>
                          )}
                        </Box>
                      </Box>
                    ) : (
                      <Typography variant="body2" color="textSecondary">
                        No threshold configured
                      </Typography>
                    )}
                  </Card>
                </Grid>

                {/* Workflow Configuration */}
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, height: '100%' }}>
                    <Typography variant="subtitle2" color="primary" fontWeight={600} gutterBottom>
                      Payment Workflow
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    {bucketConfig.workflowConfig ? (
                      <Box>
                        <Typography variant="body2" fontWeight={600} gutterBottom>
                          {bucketConfig.workflowConfig.configName}
                        </Typography>
                        <Box sx={{ mt: 1 }}>
                          <Typography variant="body2" color="textSecondary">
                            Workflow Mode:{' '}
                            <Chip
                              label={bucketConfig.workflowConfig.workflowMode}
                              size="small"
                              color={
                                bucketConfig.workflowConfig.workflowMode === 'NONE' ? 'default' :
                                bucketConfig.workflowConfig.workflowMode === 'COMBINED' ? 'success' : 'warning'
                              }
                            />
                          </Typography>
                          <Typography variant="body2" color="textSecondary" sx={{ mt: 0.5 }}>
                            Assignment Mode:{' '}
                            <Chip
                              label={bucketConfig.workflowConfig.assignmentMode}
                              size="small"
                              color={
                                bucketConfig.workflowConfig.assignmentMode === 'AUTO' ? 'success' :
                                bucketConfig.workflowConfig.assignmentMode === 'BOTH' ? 'info' : 'default'
                              }
                            />
                          </Typography>
                          <Typography variant="body2" color="textSecondary" sx={{ mt: 0.5 }}>
                            Acknowledgment Required:{' '}
                            <strong>{bucketConfig.workflowConfig.requireAcknowledgment ? 'Yes' : 'No'}</strong>
                          </Typography>
                        </Box>
                        {bucketConfig.workflowConfig.description && (
                          <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
                            {bucketConfig.workflowConfig.description}
                          </Typography>
                        )}
                      </Box>
                    ) : (
                      <Typography variant="body2" color="textSecondary">
                        No workflow configured (EFT/default)
                      </Typography>
                    )}
                  </Card>
                </Grid>

                {/* Commit Criteria */}
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, height: '100%' }}>
                    <Typography variant="subtitle2" color="primary" fontWeight={600} gutterBottom>
                      Commit Criteria
                    </Typography>
                    <Divider sx={{ my: 1 }} />
                    {bucketConfig.commitCriteria ? (
                      <Box>
                        <Typography variant="body2" fontWeight={600} gutterBottom>
                          {bucketConfig.commitCriteria.criteriaName}
                        </Typography>
                        <Chip
                          label={bucketConfig.commitCriteria.commitMode}
                          size="small"
                          color={
                            bucketConfig.commitCriteria.commitMode === 'AUTO' ? 'success' :
                            bucketConfig.commitCriteria.commitMode === 'MANUAL' ? 'warning' : 'info'
                          }
                          sx={{ mb: 1 }}
                        />
                        <Box sx={{ mt: 1 }}>
                          {bucketConfig.commitCriteria.autoCommitThreshold && (
                            <Typography variant="body2" color="textSecondary">
                              Auto-Commit Threshold: <strong>{bucketConfig.commitCriteria.autoCommitThreshold}</strong>
                            </Typography>
                          )}
                          {bucketConfig.commitCriteria.manualApprovalThreshold && (
                            <Typography variant="body2" color="textSecondary">
                              Manual Approval Threshold: <strong>{bucketConfig.commitCriteria.manualApprovalThreshold}</strong>
                            </Typography>
                          )}
                          {bucketConfig.commitCriteria.approvalRequiredRoles && bucketConfig.commitCriteria.approvalRequiredRoles.length > 0 && (
                            <Typography variant="body2" color="textSecondary">
                              Required Roles: <strong>{bucketConfig.commitCriteria.approvalRequiredRoles.join(', ')}</strong>
                            </Typography>
                          )}
                        </Box>
                      </Box>
                    ) : (
                      <Typography variant="body2" color="textSecondary">
                        No commit criteria configured (default: auto)
                      </Typography>
                    )}
                  </Card>
                </Grid>
              </Grid>
            ) : (
              <Alert severity="info">
                No specific configuration found for this bucket. Using default system settings.
              </Alert>
            )}
          </CardContent>
        </Card>
      )}

      {/* Actions */}
      <Card>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            Available Actions
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Box display="flex" flexDirection="column" gap={2}>
            {bucket.status === BucketStatus.ACCUMULATING && (
              <Box display="flex" gap={2} flexWrap="wrap">
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
              </Box>
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
              <Alert severity="info">
                This bucket is currently generating an EDI file. Please wait for the process to complete.
              </Alert>
            )}

            {bucket.status === BucketStatus.COMPLETED && (
              <Alert severity="success">
                This bucket has been completed. View the generated file in the File Management section.
              </Alert>
            )}

            {bucket.status === BucketStatus.MISSING_CONFIGURATION && configCheck && (
              <>
                <Alert severity="warning" icon={<WarningIcon />}>
                  <Typography variant="body2" fontWeight={600} gutterBottom>
                    Missing Configuration
                  </Typography>
                  <Typography variant="body2">
                    This bucket cannot generate an EDI file because required configuration is missing.
                    Please create the missing {configCheck.actionRequired === 'CREATE_BOTH' ? 'payer and payee' : configCheck.actionRequired === 'CREATE_PAYER' ? 'payer' : 'payee'} configuration.
                  </Typography>
                </Alert>

                <Box display="flex" gap={2} flexWrap="wrap">
                  {(configCheck.actionRequired === 'CREATE_PAYER' || configCheck.actionRequired === 'CREATE_BOTH') && (
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<AddIcon />}
                      onClick={() => setCreatePayerOpen(true)}
                    >
                      Create Payer: {configCheck.missingPayerId || configCheck.missingPayerName}
                    </Button>
                  )}

                  {(configCheck.actionRequired === 'CREATE_PAYEE' || configCheck.actionRequired === 'CREATE_BOTH') && (
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<AddIcon />}
                      onClick={() => setCreatePayeeOpen(true)}
                    >
                      Create Payee: {configCheck.missingPayeeId || configCheck.missingPayeeName}
                    </Button>
                  )}
                </Box>
              </>
            )}

            {bucket.status === BucketStatus.FAILED && (
              <>
                <Alert severity="error" icon={<ErrorIcon />}>
                  <Typography variant="body2" fontWeight={600} gutterBottom>
                    Processing Failed
                  </Typography>
                  <Typography variant="body2">
                    This bucket has failed during processing. {configCheck && !configCheck.hasAllConfiguration
                      ? 'Missing configuration detected. Please create the required payer/payee configuration below.'
                      : 'You can retry processing or investigate the error.'}
                  </Typography>
                </Alert>

                {/* Error Details Section */}
                {bucket.lastErrorMessage && (
                  <Card variant="outlined" sx={{ mt: 2, borderColor: 'error.main' }}>
                    <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                      <Box
                        display="flex"
                        justifyContent="space-between"
                        alignItems="center"
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setErrorExpanded(!errorExpanded)}
                      >
                        <Box display="flex" alignItems="center" gap={1}>
                          <ErrorIcon color="error" fontSize="small" />
                          <Typography variant="subtitle2" color="error.main">
                            Error Details
                          </Typography>
                          {bucket.lastErrorAt && (
                            <Typography variant="caption" color="textSecondary" sx={{ ml: 1 }}>
                              ({new Date(bucket.lastErrorAt).toLocaleString()})
                            </Typography>
                          )}
                        </Box>
                        {errorExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                      </Box>

                      {/* First sentence always visible */}
                      <Typography
                        variant="body2"
                        color="error.dark"
                        sx={{
                          mt: 1,
                          fontFamily: 'monospace',
                          fontSize: '0.85rem',
                          whiteSpace: errorExpanded ? 'pre-wrap' : 'nowrap',
                          overflow: 'hidden',
                          textOverflow: errorExpanded ? 'unset' : 'ellipsis',
                        }}
                      >
                        {errorExpanded
                          ? bucket.lastErrorMessage
                          : bucket.lastErrorMessage.split('.')[0] + (bucket.lastErrorMessage.includes('.') ? '...' : '')}
                      </Typography>

                      {!errorExpanded && bucket.lastErrorMessage.length > 100 && (
                        <Typography variant="caption" color="primary" sx={{ mt: 0.5, display: 'block' }}>
                          Click to see full error message
                        </Typography>
                      )}
                    </CardContent>
                  </Card>
                )}

                {configCheck && !configCheck.hasAllConfiguration && (
                  <Box display="flex" gap={2} flexWrap="wrap">
                    {(configCheck.actionRequired === 'CREATE_PAYER' || configCheck.actionRequired === 'CREATE_BOTH') && (
                      <Button
                        variant="contained"
                        color="primary"
                        startIcon={<AddIcon />}
                        onClick={() => setCreatePayerOpen(true)}
                      >
                        Create Payer: {configCheck.missingPayerId || configCheck.missingPayerName}
                      </Button>
                    )}

                    {(configCheck.actionRequired === 'CREATE_PAYEE' || configCheck.actionRequired === 'CREATE_BOTH') && (
                      <Button
                        variant="contained"
                        color="primary"
                        startIcon={<AddIcon />}
                        onClick={() => setCreatePayeeOpen(true)}
                      >
                        Create Payee: {configCheck.missingPayeeId || configCheck.missingPayeeName}
                      </Button>
                    )}
                  </Box>
                )}

                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<PlayArrowIcon />}
                  onClick={() => handleTransitionClick('approval')}
                >
                  Retry (Transition to Approval)
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

      {/* Create Payer Form */}
      {configCheck && (
        <CreatePayerForm
          open={createPayerOpen}
          onClose={() => setCreatePayerOpen(false)}
          bucketId={bucketId!}
          payerId={configCheck.missingPayerId || bucket.payerId}
          payerName={configCheck.missingPayerName || bucket.payerName}
          onSubmit={createPayerMutation.mutateAsync}
          isSubmitting={createPayerMutation.isPending}
        />
      )}

      {/* Create Payee Form */}
      {configCheck && (
        <CreatePayeeForm
          open={createPayeeOpen}
          onClose={() => setCreatePayeeOpen(false)}
          bucketId={bucketId!}
          payeeId={configCheck.missingPayeeId || bucket.payeeId}
          payeeName={configCheck.missingPayeeName || bucket.payeeName}
          onSubmit={createPayeeMutation.mutateAsync}
          isSubmitting={createPayeeMutation.isPending}
        />
      )}

      {/* Replace Check Dialog */}
      {checkPayment && (
        <ReplaceCheckDialog
          open={replaceCheckOpen}
          onClose={() => setReplaceCheckOpen(false)}
          bucketId={bucketId!}
          currentCheckNumber={checkPayment.checkNumber}
          currentCheckAmount={checkPayment.checkAmount}
          onSubmit={replaceCheckMutation.mutateAsync}
          isSubmitting={replaceCheckMutation.isPending}
        />
      )}
    </Box>
  );
};

export default BucketDetails;
