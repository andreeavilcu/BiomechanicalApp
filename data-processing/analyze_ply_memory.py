#!/usr/bin/env python3
"""
Analizează cerințele de memorie pentru fișiere .ply
Afișează: dimensiunea fișierului, numărul de puncte, memoria estimată necesară
"""

import sys
import os

def analyze_ply_file(filepath):
    """
    Analizează un fișier .ply și estimează memoria necesară.
    
    Returns:
        dict cu informații despre fișier
    """
    if not os.path.exists(filepath):
        print(f"❌ Fișierul nu există: {filepath}")
        return None
    
    # 1. Dimensiunea fișierului pe disk
    file_size_bytes = os.path.getsize(filepath)
    file_size_mb = file_size_bytes / (1024 * 1024)
    file_size_gb = file_size_bytes / (1024 * 1024 * 1024)
    
    print("="*70)
    print(f"📁 FIȘIER: {os.path.basename(filepath)}")
    print("="*70)
    print(f"Locație: {os.path.abspath(filepath)}")
    print(f"Dimensiune pe disk: {file_size_mb:.2f} MB ({file_size_bytes:,} bytes)")
    
    # 2. Citește header-ul pentru a afla numărul de puncte
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            vertex_count = 0
            in_header = True
            format_type = "unknown"
            
            for line in f:
                line = line.strip()
                
                # Detectează formatul
                if line.startswith('format'):
                    parts = line.split()
                    if len(parts) >= 2:
                        format_type = parts[1]  # ascii, binary_little_endian, etc.
                
                # Găsește numărul de vertecși (puncte)
                if line.startswith('element vertex'):
                    parts = line.split()
                    if len(parts) >= 3:
                        vertex_count = int(parts[2])
                
                # Sfârșitul header-ului
                if line == 'end_header':
                    in_header = False
                    break
            
            if vertex_count == 0:
                print("⚠️  Nu s-a putut detecta numărul de puncte din header")
                return None
            
            print(f"Format: {format_type}")
            print(f"Număr de puncte (vertices): {vertex_count:,}")
            
            # 3. Estimează memoria necesară
            
            # Fiecare punct în Open3D (Python) necesită aproximativ:
            # - 3 float pentru coordonate (x, y, z) = 3 * 4 bytes = 12 bytes
            # - 3 float pentru normale (opțional) = 12 bytes
            # - 3 float pentru culori (opțional) = 12 bytes
            # - Overhead Python object = ~56 bytes
            # TOTAL per punct: ~40-100 bytes (depinde de properties)
            
            bytes_per_point_min = 40   # Doar coordonate
            bytes_per_point_avg = 70   # Coordonate + culori sau normale
            bytes_per_point_max = 100  # Coordonate + culori + normale + overhead
            
            # Calculează memoria estimată
            memory_min_mb = (vertex_count * bytes_per_point_min) / (1024 * 1024)
            memory_avg_mb = (vertex_count * bytes_per_point_avg) / (1024 * 1024)
            memory_max_mb = (vertex_count * bytes_per_point_max) / (1024 * 1024)
            
            print()
            print("="*70)
            print("💾 ESTIMARE MEMORIE NECESARĂ")
            print("="*70)
            print(f"Minim (doar coordonate):     {memory_min_mb:8.2f} MB")
            print(f"Mediu (coord + culori):      {memory_avg_mb:8.2f} MB")
            print(f"Maxim (coord + culori + normale): {memory_max_mb:8.2f} MB")
            
            # 4. Estimează memoria pentru procesare (Open3D + MediaPipe)
            
            # Open3D loading = 1x memoria cloud-ului
            # MediaPipe processing = ~2-3x (conversie în imagini, detecție)
            # Total overhead = ~4-5x
            
            processing_multiplier = 5
            memory_processing_mb = memory_avg_mb * processing_multiplier
            memory_processing_gb = memory_processing_mb / 1024
            
            print()
            print("="*70)
            print("🔧 MEMORIE NECESARĂ PENTRU PROCESARE (Python + AI)")
            print("="*70)
            print(f"Cloud în memorie:            {memory_avg_mb:8.2f} MB")
            print(f"Procesare Open3D + MediaPipe: {memory_processing_mb:8.2f} MB ({memory_processing_gb:.2f} GB)")
            print()
            print("⚠️  Recomandare: Python trebuie să aibă acces la cel puțin")
            print(f"    {memory_processing_gb:.1f} GB RAM liber pentru procesare sigură")
            
            # 5. Estimează memoria pentru Spring Boot (upload)
            
            # Spring Boot trebuie să țină fișierul în memorie temporar
            # Multipart upload = ~2-3x dimensiunea fișierului
            # JVM overhead = ~1.5x
            
            upload_multiplier = 3
            memory_upload_mb = file_size_mb * upload_multiplier
            memory_upload_gb = memory_upload_mb / 1024
            
            print()
            print("="*70)
            print("☕ MEMORIE NECESARĂ PENTRU SPRING BOOT (Upload)")
            print("="*70)
            print(f"Fișier pe disk:              {file_size_mb:8.2f} MB")
            print(f"Memorie pentru upload:       {memory_upload_mb:8.2f} MB ({memory_upload_gb:.2f} GB)")
            print()
            print("⚠️  Recomandare JVM Heap: -Xmx{:.0f}g (minim)".format(
                max(2, memory_upload_gb * 1.5)  # Minimum 2GB
            ))
            
            # 6. Configurație necesară Spring Boot
            print()
            print("="*70)
            print("⚙️  CONFIGURAȚIE SPRING BOOT NECESARĂ")
            print("="*70)
            
            # Rotunjește în sus la următoarea limită (100MB, 200MB, 500MB, 1GB, etc.)
            if file_size_mb <= 100:
                config_limit = "100MB"
            elif file_size_mb <= 200:
                config_limit = "200MB"
            elif file_size_mb <= 500:
                config_limit = "500MB"
            elif file_size_mb <= 1024:
                config_limit = "1GB"
            else:
                config_limit = f"{int(file_size_mb / 1024) + 1}GB"
            
            print(f"spring.servlet.multipart.max-file-size: {config_limit}")
            print(f"spring.servlet.multipart.max-request-size: {config_limit}")
            print(f"spring.codec.max-in-memory-size: {config_limit}")
            
            # 7. Timp estimat de procesare
            print()
            print("="*70)
            print("⏱️  TIMP ESTIMAT DE PROCESARE")
            print("="*70)
            
            # Aproximativ 1-2 secunde per 10,000 puncte pentru AI processing
            seconds_per_10k = 1.5
            estimated_time_seconds = (vertex_count / 10000) * seconds_per_10k
            estimated_time_minutes = estimated_time_seconds / 60
            
            print(f"Upload la server:            ~{file_size_mb / 10:.0f} secunde (la 10 MB/s)")
            print(f"AI Processing (MediaPipe):   ~{estimated_time_minutes:.1f} minute")
            print(f"Total estimat:               ~{estimated_time_minutes + (file_size_mb / 10 / 60):.1f} minute")
            
            print()
            print("="*70)
            
            return {
                'file_size_mb': file_size_mb,
                'vertex_count': vertex_count,
                'memory_avg_mb': memory_avg_mb,
                'memory_processing_mb': memory_processing_mb,
                'memory_upload_mb': memory_upload_mb,
                'config_limit': config_limit
            }
            
    except Exception as e:
        print(f"❌ Eroare la citirea fișierului: {e}")
        return None


def main():
    if len(sys.argv) < 2:
        print("Utilizare: python analyze_ply_memory.py <fisier.ply>")
        print()
        print("Exemplu:")
        print("  python analyze_ply_memory.py test.ply")
        print("  python analyze_ply_memory.py scans/patient_001.ply")
        sys.exit(1)
    
    filepath = sys.argv[1]
    result = analyze_ply_file(filepath)
    
    if result:
        print()
        print(" Analiza completă!")
        print()
        print("REZUMAT:")
        print(f"   - Fișier: {result['file_size_mb']:.2f} MB")
        print(f"   - Puncte: {result['vertex_count']:,}")
        print(f"   - RAM Python necesar: {result['memory_processing_mb'] / 1024:.2f} GB")
        print(f"   - JVM Heap recomandat: -Xmx{max(2, int(result['memory_upload_mb'] / 1024) + 1)}g")
        print(f"   - Config Spring Boot: {result['config_limit']}")


if __name__ == "__main__":
    main()