import 'package:flutter/material.dart';
import '../../utils/app_theme.dart';
import '../../services/api_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final ApiService _apiService = ApiService();
  final TextEditingController _urlController = TextEditingController();
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _urlController.text = _apiService.baseUrl;
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _saveUrl() async {
    setState(() {
      _isSaving = true;
    });

    try {
      String url = _urlController.text.trim();
      
      // Validar URL
      if (!url.startsWith('http://') && !url.startsWith('https://')) {
        url = 'http://$url';
      }
      
      // Asegurar que termine con /api si no lo tiene
      if (!url.endsWith('/api')) {
        if (url.endsWith('/')) {
          url = '${url}api';
        } else {
          url = '$url/api';
        }
      }

      await _apiService.setBaseUrl(url);
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('URL guardada exitosamente'),
            backgroundColor: Colors.green,
          ),
        );
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error al guardar: $e'),
            backgroundColor: AppTheme.darkRed,
          ),
        );
      }
    } finally {
      setState(() {
        _isSaving = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Configuración'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.info_outline, color: AppTheme.primaryRed),
                        SizedBox(width: 8),
                        Text(
                          'Configuración del Servidor',
                          style: TextStyle(
                            color: AppTheme.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                      ],
                    ),
                    SizedBox(height: 12),
                    Text(
                      'Configure la URL del backend ILAC. Esta URL se guarda localmente y no requiere recompilar la aplicación.',
                      style: TextStyle(color: AppTheme.lightGrey, fontSize: 14),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Ejemplos:',
                      style: TextStyle(color: AppTheme.white, fontWeight: FontWeight.bold),
                    ),
                    Text(
                      '• Emulador Android: http://10.0.2.2:8080/api\n'
                      '• Web/Windows: http://localhost:8080/api\n'
                      '• Servidor real: http://192.168.1.100:8080/api',
                      style: TextStyle(color: AppTheme.grey, fontSize: 13),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
            
            const Text(
              'URL del Backend',
              style: TextStyle(
                color: AppTheme.white,
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 8),
            
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(
                hintText: 'http://10.0.2.2:8080/api',
                hintStyle: TextStyle(color: AppTheme.grey),
                prefixIcon: Icon(Icons.link, color: AppTheme.lightGrey),
              ),
              keyboardType: TextInputType.url,
            ),
            const SizedBox(height: 24),
            
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: _isSaving ? null : _saveUrl,
                icon: _isSaving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppTheme.white,
                        ),
                      )
                    : const Icon(Icons.save),
                label: const Text('Guardar Configuración'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
