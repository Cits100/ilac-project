import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../services/database_service.dart';
import '../services/auth_service.dart';

class ConnectivityService {
  static final ConnectivityService _instance = ConnectivityService._internal();
  factory ConnectivityService() => _instance;
  ConnectivityService._internal();

  final Connectivity _connectivity = Connectivity();
  final DatabaseService _dbService = DatabaseService();
  final AuthService _authService = AuthService();

  StreamSubscription<List<ConnectivityResult>>? _subscription;
  bool _isProcessing = false;

  // Callback para notificar acciones fallidas
  Function(String actionType, String taskId, String error)? onActionFailed;

  void initialize() {
    _subscription = _connectivity.onConnectivityChanged.listen((results) {
      if (results.isNotEmpty && results.first != ConnectivityResult.none) {
        _processQueue();
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
      _processQueue();
    }
  }

  Future<void> _processQueue() async {
    if (_isProcessing) return;
    _isProcessing = true;

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

        // Si ya falló 3 veces, notificar y no reintentar más
        if (retryCount >= 3) {
          final error = item['lastError'] as String? ?? 'Número máximo de reintentos alcanzado';
          onActionFailed?.call(actionType, taskId, error);
          // NO eliminar - mantener para que el usuario pueda ver
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
        } else {
          await _dbService.incrementRetryCount(id);
          if (errorMessage != null) {
            await _dbService.updateQueueItemError(id, errorMessage);
          }
        }
      }
    } catch (e) {
      // Will retry next time
    } finally {
      _isProcessing = false;
    }
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
}
