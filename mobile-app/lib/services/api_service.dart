import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/work_order.dart';

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;
  ApiService._internal();

  // Change this to your backend URL
  // For Android emulator use 10.0.2.2 instead of localhost
  // For iOS simulator use localhost
  // For real device use your computer's IP
  static const String baseUrl = 'http://10.0.2.2:8080/api';

  Future<Map<String, dynamic>> login(String identity, String credential) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/dashboard'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'identity': identity,
          'credential': credential,
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data['success'] == true) {
          // Parse work orders
          List<WorkOrder> teamOrders = [];
          List<WorkOrder> myOrders = [];

          if (data['workOrders'] != null) {
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
            'userName': data['userName'],
            'teamWorkOrders': teamOrders,
            'myWorkOrders': myOrders,
          };
        } else {
          return {
            'success': false,
            'message': data['message'] ?? 'Error de inicio de sesión',
          };
        }
      } else {
        return {
          'success': false,
          'message': 'Error del servidor: ${response.statusCode}',
        };
      }
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> addComment(
    String identity,
    String credential,
    String taskId,
    String comment, {
    String? imageBase64,
    String? imageName,
  }) async {
    try {
      final body = {
        'identity': identity,
        'credential': credential,
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
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      );

      if (response.statusCode == 200) {
        return jsonDecode(response.body);
      } else {
        return {
          'success': false,
          'message': 'Error del servidor: ${response.statusCode}',
        };
      }
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }

  Future<Map<String, dynamic>> completeTask(
    String identity,
    String credential,
    String taskId,
  ) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/complete-task'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'identity': identity,
          'credential': credential,
          'taskId': taskId,
        }),
      );

      if (response.statusCode == 200) {
        return jsonDecode(response.body);
      } else {
        return {
          'success': false,
          'message': 'Error del servidor: ${response.statusCode}',
        };
      }
    } catch (e) {
      return {
        'success': false,
        'message': 'Error de conexión: $e',
      };
    }
  }
}
