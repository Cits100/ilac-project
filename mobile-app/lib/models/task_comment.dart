class TaskComment {
  final String id;
  final String date;
  final String author;
  final String text;
  final String imageUrl;
  final String fileName;
  final bool isPendingSync;

  TaskComment({
    required this.id,
    required this.date,
    required this.author,
    required this.text,
    required this.imageUrl,
    required this.fileName,
    this.isPendingSync = false,
  });

  factory TaskComment.fromJson(Map<String, dynamic> json) {
    return TaskComment(
      id: json['id'] ?? '',
      date: json['date'] ?? '',
      author: json['author'] ?? '',
      text: json['text'] ?? '',
      imageUrl: json['imageUrl'] ?? '',
      fileName: json['fileName'] ?? '',
      isPendingSync: json['isPendingSync'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'date': date,
      'author': author,
      'text': text,
      'imageUrl': imageUrl,
      'fileName': fileName,
      'isPendingSync': isPendingSync,
    };
  }
}
