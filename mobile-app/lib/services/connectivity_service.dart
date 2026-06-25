import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../services/database_service.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';

class ConnectivityService {
  static final ConnectivityService _instance = ConnectivityService._internal();
  factory ConnectivityService() => _instance;
  ConnectivityService._internal();

  final Connectivity _connectivity = Connectivity();
  final DatabaseService _dbService = DatabaseService();
  final AuthService _authService = AuthService();
  final ApiService _apiService = ApiService();

  StreamSubscription<List<ConnectivityResult>>? _subscription;
  Timer? _periodicTimer;
  bool _isProcessing = false;

  // Callback para notificar acciones fallidas
  Function(String actionType, String taskId, String error)? onActionFailed;
  // Callback para notificar acciones exitosas
  Function(String actionType, String taskId)? onActionSuccess;

  /// Inicializar el servicio
  void initialize() {
    // Escuchar cambios de conectividad
    _subscription = _connectivity.onConnectivityChanged.listen((results) {
      if (results.isNotEmpty && results.first != ConnectivityResult.none) {
        processQueue();
      }
    });

    // Procesar cola periódicamente (cada 30 segundos)
    _periodicTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      processQueue();
    });

    // Procesar cola inmediatamente al iniciar
    Future.delayed(const Duration(seconds: 5), () {
      processQueue();
    });
  }

  void dispose() {
    _subscription?.cancel();
    _periodicTimer?.cancel();
  }

  Future<bool> isConnected() async {
    var results = await _connectivity.checkConnectivity();
    return results.isNotEmpty && results.first != ConnectivityResult.none;
  }

  /// Agregar acción a la cola
  Future<void> addToQueue(String actionType, String taskId,
      {String? comment, String? imageBase64, String? imageName, String? reason}) async {
    
    // Obtener token actual
    final token = _authService.currentSessionToken;
    
    await _dbService.addToQueue(
      actionType,
      taskId,
      comment: comment,
      imageBase64: imageBase64,
      imageName: imageName,
      reason: reason,
      sessionToken: token,
    );

    // Intentar procesar inmediatamente si hay conexión
    if (await isConnected()) {
      await processQueue();
    }
  }

  /// Procesar cola de acciones pendientes
  Future<void> processQueue() async {
    if (_isProcessing) return;
    _isProcessing = true;

    try {
      final items = await _dbService.getQueueItems();
      
      if (items.isEmpty) {
        _isProcessing = false;
        return;
      }

      print('Procesando ${items.length} acciones pendientes...');

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
          continue;
        }

        // Usar token actual (no el almacenado en la cola)
        final tokenToUse = _authService.currentSessionToken;
        
        if (tokenToUse == null) {
          // No hay sesión activa, no procesar (se procesará al re-login)
          print('No hay sesión activa, saltando acción: $actionType para tarea $taskId');
          continue;
        }

        bool success = false;
        String? errorMessage;

        try {
          if (actionType == 'comment') {
            final result = await _apiService.addComment(
              tokenToUse,
              taskId,
              comment ?? '',
              imageBase64: imageBase64,
              imageName: imageName,
            );
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'complete') {
            final result = await _apiService.completeTask(tokenToUse, taskId);
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'reject') {
            final result = await _apiService.rejectTask(tokenToUse, taskId, reason ?? '');
            success = result['success'] == true;
            errorMessage = result['message'];
          } else if (actionType == 'accept') {
            final result = await _apiService.acceptTask(tokenToUse, taskId);
            success = result['success'] == true;
            errorMessage = result['message'];
          }
        } catch (e) {
          errorMessage = e.toString();
        }

        if (success) {
          await _dbService.removeQueueItem(id);
          onActionSuccess?.call(actionType, taskId);
          print('Acción completada: $actionType para tarea $taskId');
        } else {
          await _dbService.incrementRetryCount(id);
          if (errorMessage != null) {
            await _dbService.updateQueueItemError(id, errorMessage);
          }
          print('Acción fallida: $actionType para tarea $taskId - $errorMessage');
        }
      }
    } catch (e) {
      print('Error procesando cola: $e');
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
