import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/work_order.dart';

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;
  ApiService._internal();

  // URL base configurable - se guarda en SharedPreferences
  String _baseUrl = 'http://10.0.2.2:8080/api';
  
  /// Obtener URL base actual
  String get baseUrl => _baseUrl;
  
  /// Configurar URL base y guardarla
  Future<void> setBaseUrl(String url) async {
    _baseUrl = url;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('backend_url', url);
  }
  
  /// Cargar URL base guardada
  Future<void> loadBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    final savedUrl = prefs.getString('backend_url');
    if (savedUrl != null && savedUrl.isNotEmpty) {
      _baseUrl = savedUrl;
    }
  }

  /// Crear headers con token de autorización
  Map<String, String> _authHeaders(String token) {
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $token',
    };
  }

  /// Crear headers sin autorización
  Map<String, String> get _publicHeaders => {
    'Content-Type': 'application/json',
  };

  // ==================== ENDPOINTS PÚBLICOS ====================

  Future<Map<String, dynamic>> login(String identity, String credential) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/dashboard'),
        headers: _publicHeaders,
        body: jsonEncode({
          'identity': identity,
          'credential': credential,
        }),
      );

      return _parseDashboardResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
        'errorType': 'connection',
      };
    }
  }

  // ==================== ENDPOINTS PROTEGIDOS ====================

  Future<Map<String, dynamic>> refreshData(String token) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/refresh'),
        headers: _authHeaders(token),
      );

      return _parseDashboardResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
        'errorType': 'connection',
      };
    }
  }

  Future<Map<String, dynamic>> addComment(
    String token,
    String taskId,
    String comment, {
    String? imageBase64,
    String? imageName,
  }) async {
    try {
      final body = {
        'taskId': taskId,
        'comment': comment,
      };

      if (imageBase64 != null) {
        body['imageBase64'] = imageBase64;
      }
      if (imageName != null) {
        body['imageName'] = imageName;
      }

      final response = await http.post(
        Uri.parse('$baseUrl/comment'),
        headers: _authHeaders(token),
        body: jsonEncode(body),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> completeTask(String token, String taskId) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/complete-task'),
        headers: _authHeaders(token),
        body: jsonEncode({'taskId': taskId}),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> rejectTask(String token, String taskId, String reason) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/reject-task'),
        headers: _authHeaders(token),
        body: jsonEncode({
          'taskId': taskId,
          'reason': reason,
        }),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> acceptTask(String token, String taskId) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/accept-task'),
        headers: _authHeaders(token),
        body: jsonEncode({'taskId': taskId}),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> getTaskComments(String token, String taskId) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/task-comments'),
        headers: _authHeaders(token),
        body: jsonEncode({'taskId': taskId}),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> editComment(String token, String commentId, String comment) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/edit-comment'),
        headers: _authHeaders(token),
        body: jsonEncode({
          'commentId': commentId,
          'comment': comment,
        }),
      );

      return _parseResponse(response);
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  // ==================== PARSERS ====================

  Map<String, dynamic> _parseDashboardResponse(http.Response response) {
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      if (data['success'] == true) {
        List<WorkOrder> newOrders = [];
        List<WorkOrder> teamOrders = [];
        List<WorkOrder> myOrders = [];

        if (data['workOrders'] != null) {
          if (data['workOrders']['newWorkOrders'] != null) {
            newOrders = (data['workOrders']['newWorkOrders'] as List)
                .map((o) => WorkOrder.fromJson(o, 'new'))
                .toList();
          }
          if (data['workOrders']['teamWorkOrders'] != null) {
            teamOrders = (data['workOrders']['teamWorkOrders'] as List)
                .map((o) => WorkOrder.fromJson(o, 'team'))
                .toList();
          }
          if (data['workOrders']['myWorkOrders'] != null) {
            myOrders = (data['workOrders']['myWorkOrders'] as List)
                .map((o) => WorkOrder.fromJson(o, 'personal'))
                .toList();
          }
        }

        return {
          'success': true,
          'message': data['message'],
          'sessionToken': data['sessionToken'],
          'userName': data['userName'],
          'newWorkOrders': newOrders,
          'teamWorkOrders': teamOrders,
          'myWorkOrders': myOrders,
        };
      } else {
        return {
          'success': false,
          'message': data['message'] ?? 'Error desconocido',
        };
      }
    } else if (response.statusCode == 401) {
      return {
        'success': false,
        'message': 'Sesión expirada',
        'errorType': 'session_expired',
      };
    } else {
      String message = 'Error del servidor: ${response.statusCode}';
      try {
        final body = jsonDecode(response.body);
        if (body['message'] != null) message = body['message'];
      } catch (_) {}

      return {
        'success': false,
        'message': message,
        'errorType': 'server',
      };
    }
  }

  Map<String, dynamic> _parseResponse(http.Response response) {
    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    } else if (response.statusCode == 401) {
      return {
        'success': false,
        'message': 'Sesión expirada',
        'errorType': 'session_expired',
      };
    } else {
      return {
        'success': false,
        'message': 'Error del servidor: ${response.statusCode}',
      };
    }
  }
}
