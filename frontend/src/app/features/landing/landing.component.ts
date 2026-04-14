import { Component, OnInit, OnDestroy, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss'
})
export class LandingComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private animationId = 0;
  private ctx!: CanvasRenderingContext2D;
  private particles: Particle[] = [];
  private w = 0;
  private h = 0;

  readonly features = [
    {
      icon: 'scan',
      title: '3D LiDAR Scanning',
      desc: 'Processes .ply files from any LiDAR scanner — Polycam, Scaniverse or dedicated medical equipment.'
    },
    {
      icon: 'ai',
      title: 'AI Skeleton Detection',
      desc: 'The Brute-Force Scaling Engine v6 algorithm automatically extracts 33 anatomical keypoints from the 3D point cloud.'
    },
    {
      icon: 'metrics',
      title: 'Biomechanical Metrics',
      desc: 'Automatic calculation: Q Angle, Forward Head Posture, Scapular Asymmetry, global GPS and risk level.'
    },
    {
      icon: 'recommend',
      title: 'Clinical Recommendations',
      desc: 'Personalised intervention plan: recovery exercises and ergonomic tips based on detected deviations.'
    },
    {
      icon: 'history',
      title: 'History & Evolution',
      desc: 'Track progress over time. Trend charts and session comparisons for each patient.'
    },
    {
      icon: 'research',
      title: 'Research Module',
      desc: 'Anonymised aggregate data for researchers. CSV export and cohort-level statistics.'
    },
  ];

  readonly stats = [
    { value: '4', label: 'User roles', sub: 'Patient · Specialist · Researcher · Admin' },
    { value: '5+', label: 'Biomechanical metrics', sub: 'Automatic real-time calculation' },
    { value: '3D', label: 'Interactive visualisation', sub: 'Three.js with rotation and zoom' },
  ];

  ngAfterViewInit(): void {
    this.initCanvas();
    this.animate();
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationId);
    window.removeEventListener('resize', this.onResize);
  }

  private initCanvas(): void {
    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext('2d')!;
    this.resize();
    window.addEventListener('resize', this.onResize);

    for (let i = 0; i < 60; i++) {
      this.particles.push({
        x: Math.random() * this.w,
        y: Math.random() * this.h,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        r: Math.random() * 1.5 + 0.5,
        opacity: Math.random() * 0.4 + 0.1,
      });
    }
  }

  private onResize = (): void => this.resize();

  private resize(): void {
    const canvas = this.canvasRef.nativeElement;
    this.w = canvas.width  = window.innerWidth;
    this.h = canvas.height = window.innerHeight;
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    const { ctx, w, h } = this;
    ctx.clearRect(0, 0, w, h);

    for (const p of this.particles) {
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < 0) p.x = w;
      if (p.x > w) p.x = 0;
      if (p.y < 0) p.y = h;
      if (p.y > h) p.y = 0;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(56, 189, 248, ${p.opacity})`;
      ctx.fill();
    }

    for (let i = 0; i < this.particles.length; i++) {
      for (let j = i + 1; j < this.particles.length; j++) {
        const dx = this.particles[i].x - this.particles[j].x;
        const dy = this.particles[i].y - this.particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 120) {
          ctx.beginPath();
          ctx.moveTo(this.particles[i].x, this.particles[i].y);
          ctx.lineTo(this.particles[j].x, this.particles[j].y);
          ctx.strokeStyle = `rgba(56, 189, 248, ${0.06 * (1 - dist / 120)})`;
          ctx.lineWidth = 0.5;
          ctx.stroke();
        }
      }
    }
  };
}

interface Particle {
  x: number; y: number;
  vx: number; vy: number;
  r: number; opacity: number;
}