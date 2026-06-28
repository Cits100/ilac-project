import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  final FlutterLocalNotificationsPlugin _notifications = FlutterLocalNotificationsPlugin();

  Future<void> initialize() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidSettings);
    await _notifications.initialize(initSettings);
  }

  // Notificar sincronización exitosa de una acción
  Future<void> showSyncSuccess(String actionType, String taskId) async {
    String actionName = _getActionName(actionType);
    await _showNotification(
      id: DateTime.now().millisecondsSinceEpoch % 100000,
      title: 'Acción sincronizada',
      body: '$actionName para tarea #$taskId completada',
    );
  }

  // Notificar sincronización completa
  Future<void> showSyncComplete(int totalSynced) async {
    await _showNotification(
      id: 99999,
      title: 'Sincronización completa',
      body: '$totalSynced acciones sincronizadas exitosamente',
    );
  }

  // Notificar sesión expirada
  Future<void> showSessionExpired() async {
    await _showNotification(
      id: 99998,
      title: 'Sesión expirada',
      body: 'Abra la app para sincronizar las acciones pendientes',
    );
  }

  String _getActionName(String actionType) {
    switch (actionType) {
      case 'comment': return 'Comentario';
      case 'complete': return 'Tarea completada';
      case 'reject': return 'Tarea rechazada';
      case 'accept': return 'Tarea aceptada';
      default: return 'Acción';
    }
  }

  Future<void> _showNotification({
    required int id,
    required String title,
    required String body,
  }) async {
    const androidDetails = AndroidNotificationDetails(
      'ilac_sync',
      'Sincronización',
      channelDescription: 'Notificaciones de sincronización offline',
      importance: Importance.high,
      priority: Priority.high,
    );
    const details = NotificationDetails(android: androidDetails);
    await _notifications.show(id, title, body, details);
  }
}
