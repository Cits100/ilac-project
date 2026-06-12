import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../utils/app_theme.dart';
import '../../models/work_order.dart';
import '../../services/auth_service.dart';
import '../../services/connectivity_service.dart';

class TaskDetailScreen extends StatefulWidget {
  final Task task;

  const TaskDetailScreen({
    super.key,
    required this.task,
  });

  @override
  State<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends State<TaskDetailScreen> {
  final AuthService _authService = AuthService();
  final ConnectivityService _connectivityService = ConnectivityService();
  final TextEditingController _commentController = TextEditingController();
  final ImagePicker _imagePicker = ImagePicker();
  
  File? _selectedImage;
  bool _isSubmitting = false;
  bool _isCompleting = false;

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _pickImage() async {
    // Request permissions
    final cameraStatus = await Permission.camera.request();
    final storageStatus = await Permission.photos.request();

    if (cameraStatus.isGranted || storageStatus.isGranted) {
      showModalBottomSheet(
        context: context,
        backgroundColor: AppTheme.darkGrey,
        builder: (context) => SafeArea(
          child: Wrap(
            children: [
              ListTile(
                leading: const Icon(Icons.camera_alt, color: AppTheme.white),
                title: const Text('Tomar foto', style: TextStyle(color: AppTheme.white)),
                onTap: () async {
                  Navigator.pop(context);
                  final image = await _imagePicker.pickImage(source: ImageSource.camera);
                  if (image != null) {
                    setState(() {
                      _selectedImage = File(image.path);
                    });
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.photo_library, color: AppTheme.white),
                title: const Text('Seleccionar de galería', style: TextStyle(color: AppTheme.white)),
                onTap: () async {
                  Navigator.pop(context);
                  final image = await _imagePicker.pickImage(source: ImageSource.gallery);
                  if (image != null) {
                    setState(() {
                      _selectedImage = File(image.path);
                    });
                  }
                },
              ),
            ],
          ),
        ),
      );
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Se requieren permisos de cámara y almacenamiento'),
            backgroundColor: AppTheme.darkRed,
          ),
        );
      }
    }
  }

  Future<void> _submitComment() async {
    if (_commentController.text.trim().isEmpty && _selectedImage == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Ingrese un comentario o seleccione una imagen'),
          backgroundColor: AppTheme.darkRed,
        ),
      );
      return;
    }

    setState(() {
      _isSubmitting = true;
    });

    String? imageBase64;
    String? imageName;

    if (_selectedImage != null) {
      final bytes = await _selectedImage!.readAsBytes();
      imageBase64 = base64Encode(bytes);
      imageName = _selectedImage!.path.split('/').last;
    }

    // Check connectivity
    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      // Submit directly
      final result = await _authService.addComment(
        widget.task.id,
        _commentController.text.trim(),
        imageBase64: imageBase64,
        imageName: imageName,
      );

      if (result['success'] == true) {
        _commentController.clear();
        setState(() {
          _selectedImage = null;
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Comentario agregado exitosamente'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        // Add to queue if failed
        await _connectivityService.addToQueue(
          'comment',
          widget.task.id,
          comment: _commentController.text.trim(),
          imageBase64: imageBase64,
          imageName: imageName,
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Comentario guardado para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      // Add to queue
      await _connectivityService.addToQueue(
        'comment',
        widget.task.id,
        comment: _commentController.text.trim(),
        imageBase64: imageBase64,
        imageName: imageName,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Comentario guardado para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isSubmitting = false;
    });
  }

  Future<void> _completeTask() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Marcar como Realizada', style: TextStyle(color: AppTheme.white)),
        content: const Text(
          '¿Está seguro que desea marcar esta tarea como realizada?',
          style: TextStyle(color: AppTheme.lightGrey),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await _doCompleteTask();
            },
            child: const Text('Confirmar', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );
  }

  Future<void> _doCompleteTask() async {
    setState(() {
      _isCompleting = true;
    });

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.completeTask(widget.task.id);

      if (result['success'] == true) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea marcada como realizada'),
              backgroundColor: Colors.green,
            ),
          );
          Navigator.pop(context, true); // Return true to refresh
        }
      } else {
        // Add to queue
        await _connectivityService.addToQueue('complete', widget.task.id);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Acción guardada para enviar cuando haya conexión'),
              backgroundColor: AppTheme.primaryRed,
            ),
          );
        }
      }
    } else {
      // Add to queue
      await _connectivityService.addToQueue('complete', widget.task.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Acción guardada para enviar cuando haya conexión'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isCompleting = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final task = widget.task;
    final detail = task.detail;
    final isCompleted = task.status == 'completed';

    return Scaffold(
      appBar: AppBar(
        title: Text('Tarea #${task.orderNumber}'),
        actions: [
          if (!isCompleted)
            IconButton(
              icon: const Icon(Icons.check_circle_outline),
              onPressed: _isCompleting ? null : _completeTask,
              tooltip: 'Marcar como realizada',
            ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Status badge
            if (isCompleted)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_circle, color: Colors.green, size: 20),
                    SizedBox(width: 8),
                    Text(
                      'TAREA COMPLETADA',
                      style: TextStyle(
                        color: Colors.green,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            if (isCompleted) const SizedBox(height: 16),

            // Task info card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Title
                    Text(
                      task.title,
                      style: const TextStyle(
                        color: AppTheme.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 20,
                      ),
                    ),
                    if (task.description.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        task.description,
                        style: const TextStyle(
                          color: AppTheme.lightGrey,
                          fontSize: 16,
                        ),
                      ),
                    ],
                    const Divider(color: AppTheme.darkGrey, height: 24),
                    
                    // Info rows
                    _buildInfoRow('Ubicación', '${task.location} / ${task.department}'),
                    _buildInfoRow('Máquina', '${task.machine} / ${task.machinePart}'),
                    _buildInfoRow('Producto', task.product),
                    _buildInfoRow('Tipo de despacho', task.dispatchType),
                    _buildInfoRow('Fecha de vencimiento', task.dueDate),
                    if (task.assignedTo.isNotEmpty)
                      _buildInfoRow('Asignado a', task.assignedTo),
                  ],
                ),
              ),
            ),
            
            // Task detail from API
            if (detail != null) ...[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Detalles del Mantenimiento',
                        style: TextStyle(
                          color: AppTheme.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const Divider(color: AppTheme.darkGrey, height: 16),
                      _buildInfoRow('Tipo de tarea', detail.taskType),
                      _buildInfoRow('Modo de aplicación', detail.applicationMode),
                      _buildInfoRow('Punto de mantenimiento', detail.maintenancePoint),
                      _buildInfoRow('Cantidad de puntos', detail.pointCount),
                      _buildInfoRow('Producto', detail.productName),
                      _buildInfoRow('Volumen', detail.productVolume),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 24),

            // Comment section
            const Text(
              'Agregar Comentario',
              style: TextStyle(
                color: AppTheme.white,
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 8),
            
            // Image preview
            if (_selectedImage != null)
              Stack(
                children: [
                  Container(
                    width: double.infinity,
                    height: 200,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(8),
                      image: DecorationImage(
                        image: FileImage(_selectedImage!),
                        fit: BoxFit.cover,
                      ),
                    ),
                  ),
                  Positioned(
                    top: 8,
                    right: 8,
                    child: CircleAvatar(
                      backgroundColor: AppTheme.darkRed,
                      radius: 16,
                      child: IconButton(
                        icon: const Icon(Icons.close, size: 16, color: AppTheme.white),
                        onPressed: () {
                          setState(() {
                            _selectedImage = null;
                          });
                        },
                      ),
                    ),
                  ),
                ],
              ),
            if (_selectedImage != null) const SizedBox(height: 8),
            
            // Comment input
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _commentController,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      hintText: 'Escriba su comentario...',
                      hintStyle: TextStyle(color: AppTheme.grey),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            
            // Action buttons
            Row(
              children: [
                // Image button
                IconButton(
                  onPressed: _pickImage,
                  icon: const Icon(Icons.image, color: AppTheme.primaryRed),
                  tooltip: 'Agregar imagen',
                ),
                IconButton(
                  onPressed: _pickImage,
                  icon: const Icon(Icons.camera_alt, color: AppTheme.primaryRed),
                  tooltip: 'Tomar foto',
                ),
                const Spacer(),
                
                // Submit button
                ElevatedButton.icon(
                  onPressed: _isSubmitting ? null : _submitComment,
                  icon: _isSubmitting
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppTheme.white,
                          ),
                        )
                      : const Icon(Icons.send),
                  label: const Text('Enviar'),
                ),
              ],
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    if (value.isEmpty) return const SizedBox.shrink();
    
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(
              '$label:',
              style: const TextStyle(
                color: AppTheme.grey,
                fontSize: 14,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: AppTheme.white,
                fontSize: 14,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
