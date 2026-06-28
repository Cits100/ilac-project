import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../services/database_service.dart';
import '../services/auth_service.dart';
import '../services/notification_service.dart';

class ConnectivityService {
  static final ConnectivityService _instance = ConnectivityService._internal();
  factory ConnectivityService() => _instance;
  ConnectivityService._internal();

  final Connectivity _connectivity = Connectivity();
  final DatabaseService _dbService = DatabaseService();
  final AuthService _authService = AuthService();
  final NotificationService _notificationService = NotificationService();

  StreamSubscription<List<ConnectivityResult>>? _subscription;
  bool _isProcessing = false;

  // Callback para notificar sesión expirada a la UI
  Function()? onSessionExpired;

  void initialize() {
    _subscription = _connectivity.onConnectivityChanged.listen((results) {
      if (results.isNotEmpty && results.first != ConnectivityResult.none) {
        processQueue();
      }
    });
  }

  void dispose() {
    _subscription?.cancel();
  }

  Future<bool> isConnected() async {
    var results = await _connectivity.checkConnectivity();
    return results.isNotEmpty && results.first != ConnectivityResult.none;
  }

  Future<void> addToQueue(String actionType, String taskId,
      {String? comment, String? imageBase64, String? imageName, String? reason}) async {
    await _dbService.addToQueue(
      actionType,
      taskId,
      comment: comment,
      imageBase64: imageBase64,
      imageName: imageName,
      reason: reason,
    );

    // Intentar procesar inmediatamente si hay conexión
    if (await isConnected()) {
      processQueue();
    }
  }

  /// Procesar cola de acciones pendientes (foreground)
  Future<void> processQueue() async {
    if (_isProcessing) return;
    _isProcessing = true;

    try {
      final items = await _dbService.getQueueItems();
      
      if (items.isEmpty) {
        _isProcessing = false;
        return;
      }

      int syncedCount = 0;
      bool sessionExpired = false;

      for (var item in items) {
        final id = item['id'] as int;
        final actionType = item['actionType'] as String;
        final taskId = item['taskId'] as String;
        final comment = item['comment'] as String?;
        final imageBase64 = item['imageBase64'] as String?;
        final imageName = item['imageName'] as String?;
        final reason = item['reason'] as String?;
        final retryCount = item['retryCount'] as int;

        // Saltar si demasiados reintentos
        if (retryCount >= 3) {
          final error = item['lastError'] as String? ?? 'Número máximo de reintentos alcanzado';
          onActionFailed?.call(actionType, taskId, error);
          continue;
        }

        bool success = false;
        String? errorMessage;

        try {
          if (actionType == 'comment') {
            final result = await _authService.addComment(
              taskId,
              comment ?? '',
              imageBase64: imageBase64,
              imageName: imageName,
            );
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'complete') {
            final result = await _authService.completeTask(taskId);
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'reject') {
            final result = await _authService.rejectTask(taskId, reason ?? '');
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'accept') {
            final result = await _authService.acceptTask(taskId);
            success = result['success'] == true;
            errorMessage = result['message'];
          }
        } catch (e) {
          errorMessage = e.toString();
        }

        if (success) {
          await _dbService.removeQueueItem(id);
          syncedCount++;
          // Notificar éxito individual
          await _notificationService.showSyncSuccess(actionType, taskId);
        } else {
          // Detectar sesión expirada
          if (errorMessage != null && 
              (errorMessage.contains('sesión') || 
               errorMessage.contains('token') ||
               errorMessage.contains('Sesión') ||
               errorMessage.contains('expirada'))) {
            sessionExpired = true;
            // Notificar sesión expirada
            await _notificationService.showSessionExpired();
            // NO eliminar de la cola - se procesará después del re-login
            break;
          }
          await _dbService.incrementRetryCount(id);
          if (errorMessage != null) {
            await _dbService.updateQueueItemError(id, errorMessage);
          }
        }
      }

      // Si se sincronizó algo, notificar
      if (syncedCount > 0) {
        await _notificationService.showSyncComplete(syncedCount);
      }

      // Si la sesión expiró, notificar a la UI
      if (sessionExpired) {
        onSessionExpired?.call();
      }
    } catch (e) {
      // Will retry next time
    } finally {
      _isProcessing = false;
    }
  }

  /// Procesar cola en background (para WorkManager)
  Future<int> processQueueInBackground() async {
    if (_isProcessing) return 0;
    _isProcessing = true;
    int syncedCount = 0;

    try {
      final items = await _dbService.getQueueItems();
      
      for (var item in items) {
        final id = item['id'] as int;
        final actionType = item['actionType'] as String;
        final taskId = item['taskId'] as String;
        final comment = item['comment'] as String?;
        final imageBase64 = item['imageBase64'] as String?;
        final imageName = item['imageName'] as String?;
        final reason = item['reason'] as String?;
        final retryCount = item['retryCount'] as int;

        if (retryCount >= 3) {
          continue;
        }

        bool success = false;

        try {
          if (actionType == 'comment') {
            final result = await _authService.addComment(
              taskId,
              comment ?? '',
              imageBase64: imageBase64,
              imageName: imageName,
            );
            success = result['success'] == true;
          } else if (actionType == 'complete') {
            final result = await _authService.completeTask(taskId);
            success = result['success'] == true;
          } else if (actionType == 'reject') {
            final result = await _authService.rejectTask(taskId, reason ?? '');
            success = result['success'] == true;
          } else if (actionType == 'accept') {
            final result = await _authService.acceptTask(taskId);
            success = result['success'] == true;
          }
        } catch (e) {
          // Error will be retried
        }

        if (success) {
          await _dbService.removeQueueItem(id);
          syncedCount++;
        } else {
          await _dbService.incrementRetryCount(id);
        }
      }
    } catch (e) {
      // Will retry next time
    } finally {
      _isProcessing = false;
    }

    return syncedCount;
  }

  Future<int> getQueueCount() async {
    final items = await _dbService.getQueueItems();
    return items.length;
  }

  Future<int> getFailedQueueCount() async {
    final items = await _dbService.getFailedQueueItems();
    return items.length;
  }

  Future<List<Map<String, dynamic>>> getFailedQueueItems() async {
    return await _dbService.getFailedQueueItems();
  }

  Future<void> clearFailedQueueItems() async {
    await _dbService.clearFailedQueueItems();
  }

  // Callback para notificar acciones fallidas
  Function(String actionType, String taskId, String error)? onActionFailed;
}
