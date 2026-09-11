import { Component } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http'; // 1. Importa HttpHeaders

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  identificacion: string = '';
  person: any = null;
  company: any = null;
  vigenciaRut: any = null;
  loading: boolean = false;
  message: string = '';
  mostrarVigenciaRutDetalle: boolean = false; // Estado para alternar la vista

  constructor(private http: HttpClient) {}

  lookup() {
    if (!this.identificacion || !this.identificacion.trim()) return;
    this.loading = true;
    this.person = null;
    this.company = null;
    this.vigenciaRut = null;
    this.mostrarVigenciaRutDetalle = false;
    this.message = '';

    const params = new URLSearchParams();
    params.set('identificacion', this.identificacion.trim());

    const headers = new HttpHeaders({
      'X-API-Key': '34809238490283409284028340923840'
    });

    this.http.get<any>(`/api/lookup?${params.toString()}`, { headers }).subscribe({
      next: (res) => {
        this.loading = false;
        this.person = res?.person || null;
        this.company = res?.company || null;
        this.vigenciaRut = res?.vigenciaRut || null;
        this.message = res?.message || '';
      },
      error: (err) => {
        this.loading = false;
        this.message = 'Error en la consulta';
      }
    });
  }

  toggleVigenciaRut() {
    this.mostrarVigenciaRutDetalle = !this.mostrarVigenciaRutDetalle;
  }
}